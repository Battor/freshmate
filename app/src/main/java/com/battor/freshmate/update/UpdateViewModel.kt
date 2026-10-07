package com.battor.freshmate.update

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.battor.freshmate.BuildConfig
import com.battor.freshmate.R
import com.battor.freshmate.ui.common.UiText
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

class UpdateViewModel(
    appContext: Context,
    private val currentVersionCode: Int = BuildConfig.VERSION_CODE,
) : ViewModel() {

    data class UiState(
        val checking: Boolean = false,
        /** 手动检查发现的新版本（对话框展示；null = 无）。 */
        val manifest: UpdateManifest? = null,
        /** 启动静默检查发现的新版本（主页 Snackbar 用，一次性）。 */
        val silentFound: UpdateManifest? = null,
        val downloading: Boolean = false,
        /** 下载进度 0..1；null = 服务器未给总长度，展示不定进度条。 */
        val progress: Float? = null,
        val apkReady: Boolean = false,
        /** 一次性结果提示（null = 不展示）。 */
        val notice: UiText? = null,
    ) {
        val busy: Boolean get() = checking || downloading
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val checker = UpdateChecker(BuildConfig.UPDATE_MANIFEST_URL)
    private val downloader = ApkDownloader()
    private val installer = ApkInstaller(appContext)
    private val apkFile: File = ApkDownloader.apkFile(appContext)

    /** 启动静默检查只做一次：返回主页/重组不应重复拉取与重复弹提示。 */
    private var silentChecked = false

    fun clearNotice() = _uiState.update { it.copy(notice = null) }
    fun clearSilentFound() = _uiState.update { it.copy(silentFound = null) }
    fun dismissManifest() = _uiState.update { it.copy(manifest = null, apkReady = false) }

    /** manual=true：结果都提示用户；false：仅发现新版时提示（设计文档 §5.2）。 */
    fun check(manual: Boolean) {
        if (_uiState.value.busy) return
        if (!manual) {
            if (silentChecked) return
            silentChecked = true
        }
        _uiState.update { it.copy(checking = true) }
        viewModelScope.launch {
            checker.fetch().fold(
                onSuccess = { m ->
                    if (isUpdateAvailable(m, currentVersionCode)) {
                        _uiState.update {
                            it.copy(
                                checking = false,
                                // 重查发现新版时清掉旧 apkReady：防止把上一版已下载的
                                // 安装包当成新版本装出去
                                apkReady = false,
                                manifest = if (manual) m else it.manifest,
                                silentFound = if (manual) null else m,
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(checking = false, notice = if (manual) UiText(R.string.update_already_latest) else null)
                        }
                    }
                },
                onFailure = {
                    _uiState.update {
                        it.copy(checking = false, notice = if (manual) UiText(R.string.update_check_failed) else null)
                    }
                },
            )
        }
    }

    fun download() {
        val m = _uiState.value.manifest ?: return
        if (_uiState.value.busy) return
        _uiState.update { it.copy(downloading = true, progress = null) }
        viewModelScope.launch {
            try {
                downloader.download(m.apkUrl, apkFile) { copied, total ->
                    _uiState.update { s ->
                        s.copy(progress = if (total > 0) copied.toFloat() / total else null)
                    }
                }
                // 校验策略（设计文档 §5.3；F-Droid 审核意见收紧）：清单必须提供 sha256，
                // 缺失视为不可信来源直接拒绝安装（Android 同签名规则仍兜底，但哈希校验更严）
                val expected = m.sha256
                if (expected == null) {
                    Timber.i("UPDATE 清单未提供 sha256，拒绝安装")
                    apkFile.delete()
                    _uiState.update { it.copy(downloading = false, notice = UiText(R.string.update_checksum_failed)) }
                    return@launch
                }
                val actual = downloader.sha256(apkFile)
                if (!actual.equals(expected, ignoreCase = true)) {
                    Timber.i("UPDATE 校验失败 expected=%s actual=%s", expected, actual)
                    apkFile.delete()
                    _uiState.update { it.copy(downloading = false, notice = UiText(R.string.update_checksum_failed)) }
                    return@launch
                }
                _uiState.update { it.copy(downloading = false, apkReady = true) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.i(e, "UPDATE 下载失败")
                apkFile.delete()
                _uiState.update { it.copy(downloading = false, notice = UiText(R.string.update_download_failed, listOf(e.message ?: e.javaClass.simpleName))) }
            }
        }
    }

    /** 未授权时跳系统授权页；用户授权返回后可再点安装。 */
    fun install() {
        if (!_uiState.value.apkReady) return
        if (!apkFile.exists()) {
            // cacheDir 可能被系统在后台清掉（存储压力）：降回未就绪并提示重下，
            // 否则 FileProvider intent 指向不存在的文件，安装器报解析错误且应用内无提示
            _uiState.update { it.copy(apkReady = false, notice = UiText(R.string.update_apk_missing)) }
            return
        }
        if (!installer.canInstall()) {
            installer.launchPermissionSettings()
            return
        }
        installer.launchInstall(apkFile)
    }
}
