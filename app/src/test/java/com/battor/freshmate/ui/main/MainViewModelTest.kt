package com.battor.freshmate.ui.main

import com.battor.freshmate.FakeRepository
import com.battor.freshmate.FakeScheduler
import com.battor.freshmate.data.Category
import com.battor.freshmate.data.FoodItem
import com.battor.freshmate.inputmethod.InputMethodId
import com.battor.freshmate.util.ExpiryStatus
import com.battor.freshmate.util.ShelfLifeUnit
import com.battor.freshmate.util.computeReminderTimes
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private var now = LocalDateTime.of(2026, 8, 15, 10, 0)
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeRepository
    private lateinit var scheduler: FakeScheduler
    private lateinit var vm: MainViewModel

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeRepository()
        scheduler = FakeScheduler()
        vm = MainViewModel(repo, scheduler) { now }
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private suspend fun TestScope.saveNew(name: String = "牛奶", shelfLife: String = "7") {
        vm.startNew(InputMethodId.MANUAL)
        vm.updateEditing { it.copy(name = name, shelfLifeValue = shelfLife) }
        vm.save()
        advanceUntilIdle()
    }

    @Test fun `新建表单的录入时间为当前时间`() = runTest(dispatcher) {
        vm.startNew(InputMethodId.MANUAL)
        assertEquals(now, vm.uiState.value.editing?.createdAt)
    }

    @Test fun `名称为空时不保存并提示错误`() = runTest(dispatcher) {
        vm.startNew(InputMethodId.MANUAL)
        vm.updateEditing { it.copy(shelfLifeValue = "7") }
        vm.save()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.editing?.nameError == true)
        assertTrue(repo.items.value.isEmpty())
    }

    @Test fun `保质期非法时不保存并提示错误`() = runTest(dispatcher) {
        vm.startNew(InputMethodId.MANUAL)
        vm.updateEditing { it.copy(name = "牛奶", shelfLifeValue = "abc") }
        vm.save()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.editing?.shelfLifeError == true)
        assertTrue(repo.items.value.isEmpty())
    }

    @Test fun `合法输入保存后入库并排提醒`() = runTest(dispatcher) {
        saveNew()
        assertEquals(1, repo.items.value.size)
        assertEquals("牛奶", repo.items.value[0].name)
        assertEquals(7, repo.items.value[0].shelfLifeDays)
        assertEquals(1, scheduler.scheduled.size)
        // 暂存后表单清空继续，条目进入「本次添加」置顶区
        val editing = vm.uiState.value.editing
        assertNotNull(editing)
        assertTrue(editing!!.name.isEmpty())
        assertEquals(setOf(repo.items.value[0].id), vm.uiState.value.sessionItemIds)
        assertEquals(1, vm.uiState.value.pinnedItems.size)
    }

    @Test fun `填写单位换算成天数入库`() = runTest(dispatcher) {
        vm.startNew(InputMethodId.MANUAL)
        vm.updateEditing { it.copy(name = "酱油", shelfLifeValue = "18", shelfLifeUnit = ShelfLifeUnit.MONTH) }
        vm.save()
        advanceUntilIdle()
        assertEquals(540, repo.items.value[0].shelfLifeDays)
    }

    @Test fun `提醒时点已过时先弹确认`() = runTest(dispatcher) {
        vm.startNew(InputMethodId.MANUAL)
        // 生产日期 8/1 + 7 天 → 8/8 已过期，真实错过的提醒时点全部已过
        vm.updateEditing {
            it.copy(name = "酸奶", shelfLifeValue = "7", productionDate = LocalDate.of(2026, 8, 1))
        }
        vm.save()
        advanceUntilIdle()
        assertTrue(repo.items.value.isEmpty())
        assertNotNull(vm.uiState.value.pendingSave)

        vm.confirmPendingSave()
        advanceUntilIdle()
        assertEquals(1, repo.items.value.size)
        assertNull(vm.uiState.value.pendingSave)
    }

    @Test fun `放弃新表单返回时置顶区不受影响`() = runTest(dispatcher) {
        vm.startNew(InputMethodId.MANUAL)
        vm.backToMethodSelection()
        assertNull(vm.uiState.value.editing)
        assertTrue(vm.uiState.value.pinnedItems.isEmpty())
        assertTrue(repo.items.value.isEmpty())
    }

    @Test fun `删除后可撤销恢复`() = runTest(dispatcher) {
        saveNew()
        val item = repo.items.value[0]
        vm.delete(item)
        advanceUntilIdle()
        assertTrue(repo.items.value.all { it.deletedAt != null }) // 软删除：仍在库，标记已删
        assertEquals(item.id, scheduler.cancelled.singleOrNull())

        vm.undoDelete(item)
        advanceUntilIdle()
        assertEquals(1, repo.getAll().size) // 撤销后回到活跃
        assertEquals("牛奶", repo.items.value[0].name)
        // FakeScheduler 全程累计：保存时 1 次 + 撤销重排 1 次
        assertEquals(2, scheduler.scheduled.size)
    }

    @Test fun `软删除条目不出现在主列表`() = runTest(dispatcher) {
        saveNew()
        vm.delete(repo.items.value[0])
        advanceUntilIdle()
        assertTrue(vm.uiState.value.items.isEmpty())
        assertTrue(vm.uiState.value.buckets.isEmpty())
    }

    @Test fun `已删除条目经撤销还原保留全部字段`() = runTest(dispatcher) {
        saveNew()
        val before = repo.items.value[0]
        vm.delete(before)
        advanceUntilIdle()
        vm.undoDelete(before)
        advanceUntilIdle()
        val after = repo.getAll().single()
        assertEquals(before.copy(deletedAt = null), after)
    }

    @Test fun `编辑已有条目保留录入时间`() = runTest(dispatcher) {
        saveNew()
        val item = repo.items.value[0]
        vm.startEdit(item)
        vm.updateEditing { it.copy(name = "鲜牛奶") }
        vm.save()
        advanceUntilIdle()
        assertEquals(1, repo.items.value.size)
        assertEquals("鲜牛奶", repo.items.value[0].name)
        assertEquals(now, repo.items.value[0].createdAt)
        // 编辑已有条目是单条保存语义：保存即退出表单
        assertNull(vm.uiState.value.editing)
    }

    @Test fun `首次保存后请求通知权限`() = runTest(dispatcher) {
        assertFalse(vm.uiState.value.requestNotificationPermission)
        saveNew()
        assertTrue(vm.uiState.value.requestNotificationPermission)
        vm.onPermissionRequested()
        assertFalse(vm.uiState.value.requestNotificationPermission)
    }

    @Test fun `取消过期确认不保存`() = runTest(dispatcher) {
        vm.startNew(InputMethodId.MANUAL)
        vm.updateEditing {
            it.copy(name = "酸奶", shelfLifeValue = "7", productionDate = LocalDate.of(2026, 8, 1))
        }
        vm.save()
        advanceUntilIdle()
        vm.cancelPendingSave()
        advanceUntilIdle()
        assertNull(vm.uiState.value.pendingSave)
        assertTrue(repo.items.value.isEmpty())
    }

    @Test fun `保存已过期食品不排提醒并取消闹钟`() = runTest(dispatcher) {
        vm.startNew(InputMethodId.MANUAL)
        vm.updateEditing {
            it.copy(name = "酸奶", shelfLifeValue = "7", productionDate = LocalDate.of(2026, 8, 1))
        }
        vm.save()
        advanceUntilIdle()
        vm.confirmPendingSave()
        advanceUntilIdle()
        val saved = repo.items.value.single()
        assertTrue(scheduler.scheduled.isEmpty())
        assertEquals(listOf(saved.id), scheduler.cancelled)
    }

    @Test fun `撤销恢复保留录入时间`() = runTest(dispatcher) {
        saveNew()
        val item = repo.items.value[0]
        vm.delete(item)
        advanceUntilIdle()
        vm.undoDelete(item)
        advanceUntilIdle()
        assertEquals(now, repo.items.value[0].createdAt)
    }

    @Test fun `第二次保存不再请求通知权限`() = runTest(dispatcher) {
        saveNew()
        vm.onPermissionRequested()
        saveNew(name = "面包")
        assertFalse(vm.uiState.value.requestNotificationPermission)
    }

    @Test fun `空白数量保存为null`() = runTest(dispatcher) {
        vm.startNew(InputMethodId.MANUAL)
        vm.updateEditing { it.copy(name = "牛奶", shelfLifeValue = "7", quantity = "   ") }
        vm.save()
        advanceUntilIdle()
        assertNull(repo.items.value[0].quantity)
    }

    @Test fun `保存失败时提示错误且保留表单`() = runTest(dispatcher) {
        repo.failNextInsert = true
        vm.startNew(InputMethodId.MANUAL)
        vm.updateEditing { it.copy(name = "牛奶", shelfLifeValue = "7") }
        vm.save()
        advanceUntilIdle()
        assertEquals("保存失败，请重试", vm.errorEvent.value)
        assertNotNull(vm.uiState.value.editing) // 表单保留
        assertTrue(repo.items.value.isEmpty())
        vm.onErrorShown()
        assertNull(vm.errorEvent.value)
        // 再次保存成功（saving 标志已在失败路径复位）
        vm.save()
        advanceUntilIdle()
        assertEquals(1, repo.items.value.size)
    }

    @Test fun `同会话多次暂存都在置顶区`() = runTest(dispatcher) {
        saveNew()
        saveNew(name = "面包")
        assertEquals(2, repo.items.value.size)
        assertEquals(2, vm.uiState.value.pinnedItems.size)
        assertTrue(vm.uiState.value.buckets.isEmpty()) // 置顶区条目不重复出现在桶中
    }

    @Test fun `暂存后表单清空但保留输入方式和分类`() = runTest(dispatcher) {
        vm.startNew(InputMethodId.VOICE)
        vm.updateEditing {
            it.copy(
                name = "牛奶",
                shelfLifeValue = "7",
                category = Category.DAIRY,
                quantity = "2",
                productionDate = LocalDate.of(2026, 8, 14),
            )
        }
        vm.save()
        advanceUntilIdle()
        val editing = vm.uiState.value.editing
        assertNotNull(editing)
        assertEquals(InputMethodId.VOICE, editing!!.inputMethod)
        assertEquals(Category.DAIRY, editing.category)
        assertEquals(now, editing.createdAt) // 会话时刻沿用，下一条仍归入同组
        assertTrue(editing.name.isEmpty())
        assertTrue(editing.shelfLifeValue.isEmpty())
        assertTrue(editing.quantity.isEmpty())
        assertNull(editing.productionDate)
    }

    @Test fun `按绝对过期时间分桶且桶序为紧急度`() {
        val now = LocalDateTime.of(2026, 8, 20, 10, 0)
        fun of(name: String, hours: Long) = FoodItem(
            name = name, category = Category.DAIRY, productionDate = null,
            shelfLifeDays = 30, quantity = null, createdAt = now.minusHours(hours),
        )
        // 到期 = createdAt + 30 天，相对 now 的剩余 = 720h - hours
        val buckets = bucketItems(
            listOf(
                of("远", 100),   // 剩 620h ≈ 25.8 天 → SAFE
                of("一", 696),   // 剩 24h → DUE_1D
                of("过", 744),   // 剩 -24h → EXPIRED
                of("三", 648),   // 剩 72h → DUE_3D
                of("七", 552),   // 剩 168h → DUE_7D
                of("十四", 384), // 剩 336h → DUE_14D
            ),
            now,
        )
        assertEquals(
            listOf(
                ExpiryStatus.EXPIRED, ExpiryStatus.DUE_1D, ExpiryStatus.DUE_3D,
                ExpiryStatus.DUE_7D, ExpiryStatus.DUE_14D, ExpiryStatus.SAFE,
            ),
            buckets.map { it.status },
        )
    }

    @Test fun `桶内按到期时间升序最紧急在前`() {
        val now = LocalDateTime.of(2026, 8, 20, 10, 0)
        fun of(name: String, hours: Long) = FoodItem(
            name = name, category = Category.DAIRY, productionDate = null,
            shelfLifeDays = 30, quantity = null, createdAt = now.minusHours(hours),
        )
        // 两条都落在 DUE_3D（剩 54h / 30h），同桶内按到期时间升序
        val buckets = bucketItems(listOf(of("晚到期", 666), of("早到期", 690)), now)
        assertEquals(1, buckets.size)
        assertEquals(listOf("早到期", "晚到期"), buckets[0].items.map { it.name })
    }

    @Test fun `空桶不渲染`() {
        val now = LocalDateTime.of(2026, 8, 20, 10, 0)
        val only = FoodItem(
            name = "a", category = Category.DAIRY, productionDate = null,
            shelfLifeDays = 30, quantity = null, createdAt = now,
        )
        assertEquals(1, bucketItems(listOf(only), now).size)
    }

    @Test fun `下拉刷新清空会话置顶区条目散入各桶`() = runTest(dispatcher) {
        saveNew() // 到期 8-22 10:00，now=8-15 10:00 → 剩整 7 天 → DUE_7D
        assertTrue(vm.uiState.value.pinnedItems.isNotEmpty())
        vm.disperseSession()
        assertTrue(vm.uiState.value.pinnedItems.isEmpty())
        assertEquals(listOf(ExpiryStatus.DUE_7D), vm.uiState.value.buckets.map { it.status })
    }

    @Test fun `空新增表单无内容填任一字段后才有`() = runTest(dispatcher) {
        vm.startNew(InputMethodId.MANUAL)
        assertFalse(vm.uiState.value.hasFormContent)
        vm.updateEditing { it.copy(productionDate = LocalDate.of(2026, 8, 14)) }
        assertTrue(vm.uiState.value.hasFormContent)
    }

    @Test fun `编辑表单未改动无内容改动后才有`() = runTest(dispatcher) {
        saveNew()
        vm.startEdit(repo.items.value[0])
        assertFalse(vm.uiState.value.hasFormContent)
        vm.updateEditing { it.copy(name = "鲜牛奶") }
        assertTrue(vm.uiState.value.hasFormContent)
    }

    @Test fun `编辑表单改数量也算有内容`() = runTest(dispatcher) {
        saveNew()
        vm.startEdit(repo.items.value[0])
        vm.updateEditing { it.copy(quantity = "2") }
        assertTrue(vm.uiState.value.hasFormContent)
    }

    @Test fun `保存时算好提醒快照落库`() = runTest(dispatcher) {
        saveNew() // now 8-15 10:00 录入，保质期 7 天 → 到期 8-22 10:00
        val saved = repo.items.value.single()
        val expected = computeReminderTimes(
            LocalDateTime.of(2026, 8, 22, 10, 0), 7, now,
        )
        assertEquals(expected, saved.reminderTimes)
        // 字面值锚点（7 天保质期、now=8-15 10:00 录入）：候选合并后为
        // [8-15 10:00, 8-19 10:00, 8-21 00:00]（1/3→8-20 02:00 距 8-19 10:00 仅 16h 被并、
        // 1/5→8-21 00:24 取整 8-21 00:00 保留，1/6=28h 与 1 天档并入 8-21 00:00 的 24h 窗口），
        // 7 天档 = now 不严格晚于 now 被滤
        assertEquals(
            listOf(
                LocalDateTime.of(2026, 8, 19, 10, 0),
                LocalDateTime.of(2026, 8, 21, 0, 0),
            ),
            saved.reminderTimes,
        )
        assertFalse(saved.reminderTimes.isEmpty()) // 至少保底一个
    }

    @Test fun `编辑置顶区条目保存后仍在置顶区`() = runTest(dispatcher) {
        saveNew()
        val item = repo.items.value[0]
        vm.startEdit(item)
        vm.updateEditing { it.copy(name = "鲜牛奶") }
        vm.save()
        advanceUntilIdle()
        assertEquals("鲜牛奶", repo.items.value[0].name)
        assertEquals(setOf(item.id), vm.uiState.value.sessionItemIds) // 编辑保存不动会话集合
        assertEquals(1, vm.uiState.value.pinnedItems.size)
    }

    @Test fun `置顶区条目删除后撤销仍在置顶区`() = runTest(dispatcher) {
        saveNew()
        val item = repo.items.value[0]
        vm.delete(item)
        advanceUntilIdle()
        vm.undoDelete(item)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.pinnedItems.isNotEmpty()) // 删除不清会话集合，撤销自然回置顶区
    }

    @Test fun `编辑改保质期后快照重算`() = runTest(dispatcher) {
        saveNew()
        vm.startEdit(repo.items.value[0])
        vm.updateEditing { it.copy(shelfLifeValue = "30") }
        vm.save()
        advanceUntilIdle()
        val saved = repo.items.value.single()
        assertEquals(
            computeReminderTimes(LocalDateTime.of(2026, 9, 14, 10, 0), 30, now),
            saved.reminderTimes,
        )
    }

    @Test fun `refreshNow刷新页面时刻`() = runTest(dispatcher) {
        saveNew()
        now = now.plusDays(3)
        vm.refreshNow()
        assertEquals(now, vm.uiState.value.now)
    }
}
