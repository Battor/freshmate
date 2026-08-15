package com.battor.freshmate.ui.main

import com.battor.freshmate.data.Category
import com.battor.freshmate.data.FoodItem
import com.battor.freshmate.data.FoodRepository
import com.battor.freshmate.inputmethod.InputMethodId
import com.battor.freshmate.notification.ReminderScheduling
import com.battor.freshmate.util.ShelfLifeUnit
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val now = LocalDateTime.of(2026, 8, 15, 10, 0)
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
        assertEquals(now, repo.items.value[0].createdAt)
        assertEquals(1, scheduler.scheduled.size)
        assertNull(vm.uiState.value.editing)
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
        // 生产日期 8/1 + 7 天 → 8/8 已过期，3 个时点全部已过
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

    @Test fun `放弃清空编辑状态`() = runTest(dispatcher) {
        vm.startNew(InputMethodId.MANUAL)
        vm.discard()
        assertNull(vm.uiState.value.editing)
        assertTrue(repo.items.value.isEmpty())
    }

    @Test fun `删除后可撤销恢复`() = runTest(dispatcher) {
        saveNew()
        val item = repo.items.value[0]
        vm.delete(item)
        advanceUntilIdle()
        assertTrue(repo.items.value.isEmpty())
        assertEquals(item.id, scheduler.cancelled.singleOrNull())

        vm.undoDelete(item)
        advanceUntilIdle()
        assertEquals(1, repo.items.value.size)
        assertEquals("牛奶", repo.items.value[0].name)
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
    }

    @Test fun `首次保存后请求通知权限`() = runTest(dispatcher) {
        assertFalse(vm.uiState.value.requestNotificationPermission)
        saveNew()
        assertTrue(vm.uiState.value.requestNotificationPermission)
        vm.onPermissionRequested()
        assertFalse(vm.uiState.value.requestNotificationPermission)
    }

    @Test fun `按录入时间倒序分组`() {
        val a = FoodItem(name = "a", category = Category.DAIRY, productionDate = null,
            shelfLifeDays = 7, quantity = null, createdAt = LocalDateTime.of(2026, 8, 15, 10, 0))
        val b = a.copy(name = "b", createdAt = LocalDateTime.of(2026, 8, 15, 10, 0, 30))
        val c = a.copy(name = "c", createdAt = LocalDateTime.of(2026, 8, 14, 9, 0))
        val groups = groupItems(listOf(c, a, b))
        assertEquals(2, groups.size)
        assertEquals(listOf("b", "a"), groups[0].items.map { it.name }) // 同分钟同组，组内倒序
        assertEquals(listOf("c"), groups[1].items.map { it.name })
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
}

class FakeRepository : FoodRepository {
    val items = MutableStateFlow<List<FoodItem>>(emptyList())
    var failNextInsert = false
    private var nextId = 1L
    override fun observeAll(): Flow<List<FoodItem>> = items
    override suspend fun insert(item: FoodItem): Long {
        if (failNextInsert) {
            failNextInsert = false
            throw RuntimeException("db error")
        }
        val id = nextId++
        items.value = items.value + item.copy(id = id)
        return id
    }
    override suspend fun update(item: FoodItem) {
        items.value = items.value.map { if (it.id == item.id) item else it }
    }
    override suspend fun delete(item: FoodItem) {
        items.value = items.value.filterNot { it.id == item.id }
    }
    override suspend fun getAll(): List<FoodItem> = items.value
}

class FakeScheduler : ReminderScheduling {
    val scheduled = mutableListOf<FoodItem>()
    val cancelled = mutableListOf<Long>()
    override fun schedule(item: FoodItem) { scheduled.add(item) }
    override fun cancel(itemId: Long) { cancelled.add(itemId) }
    override suspend fun rescheduleAll() {}
}
