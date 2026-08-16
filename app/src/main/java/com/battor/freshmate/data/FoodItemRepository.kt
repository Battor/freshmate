package com.battor.freshmate.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

interface FoodRepository {
    fun observeAll(): Flow<List<FoodItem>>
    suspend fun insert(item: FoodItem): Long
    suspend fun update(item: FoodItem)
    suspend fun delete(item: FoodItem)
    suspend fun getAll(): List<FoodItem>
}

class FoodItemRepository(private val dao: FoodItemDao) : FoodRepository {
    override fun observeAll(): Flow<List<FoodItem>> =
        dao.observeAll().onEach { Timber.i("DB observeAll ← %d items", it.size) }

    override suspend fun insert(item: FoodItem): Long =
        timed("insert(${item.summary()})") { dao.insert(item) }

    override suspend fun update(item: FoodItem) =
        timed("update(${item.summary()})") { dao.update(item) }

    override suspend fun delete(item: FoodItem) =
        timed("delete(id=${item.id} ${item.name})") { dao.delete(item) }

    override suspend fun getAll(): List<FoodItem> =
        timed("getAll", resultFormat = { "${it.size} items" }) { dao.getAllOnce() }

    /** 设计文档 §4.1：记录查询内容、返回值与耗时；失败也记（不吞异常，取消照常上抛）。 */
    private inline fun <T> timed(
        label: String,
        resultFormat: (T) -> String = { if (it == Unit) "OK" else it.toString() },
        block: () -> T,
    ): T {
        val start = System.currentTimeMillis()
        val outcome = runCatching(block)
        val t = outcome.exceptionOrNull()
        if (t is CancellationException) throw t
        outcome.onSuccess {
            Timber.i("DB %s ← %s (%dms)", label, resultFormat(it), System.currentTimeMillis() - start)
        }.onFailure {
            Timber.w(it, "DB %s 失败", label)
        }
        return outcome.getOrThrow()
    }
}

private fun FoodItem.summary(): String = "name=$name, days=$shelfLifeDays"
