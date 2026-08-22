package com.battor.freshmate.ui.history

import com.battor.freshmate.R
import com.battor.freshmate.ui.common.UiText
import com.battor.freshmate.FakeRepository
import com.battor.freshmate.FakeScheduler
import com.battor.freshmate.data.Category
import com.battor.freshmate.data.FoodItem
import com.battor.freshmate.notification.scheduleOrCancel
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private var now = LocalDateTime.of(2026, 8, 16, 10, 0)
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeRepository
    private lateinit var scheduler: FakeScheduler
    private lateinit var vm: HistoryViewModel

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeRepository()
        scheduler = FakeScheduler()
        vm = HistoryViewModel(repo, scheduler) { now }
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun active(name: String, createdAt: LocalDateTime) = FoodItem(
        name = name, category = Category.DAIRY, productionDate = null,
        shelfLifeDays = 7, quantity = null, createdAt = createdAt,
    )

    /** 插入并拿回带 id 的实体（Fake 的 insert 回填 id 但不改传入对象）。 */
    private suspend fun insertActive(name: String, createdAt: LocalDateTime): FoodItem {
        val id = repo.insert(active(name, createdAt))
        return repo.items.value.first { it.id == id }
    }

    private suspend fun TestScope.deleteActive(item: FoodItem) {
        repo.softDelete(item, now)
        advanceUntilIdle()
    }

    @Test fun `已删除条目按删除时间倒序分组`() = runTest(dispatcher) {
        val g1 = LocalDateTime.of(2026, 8, 14, 9, 0)
        val a = insertActive("牛奶", g1)
        val b = insertActive("面包", g1)
        advanceUntilIdle()
        now = LocalDateTime.of(2026, 8, 16, 9, 0)
        deleteActive(a)
        now = LocalDateTime.of(2026, 8, 16, 10, 0)
        deleteActive(b)
        val groups = vm.uiState.value.groups
        assertEquals(2, groups.size)
        assertEquals(LocalDateTime.of(2026, 8, 16, 10, 0), groups[0].deletedAt)
        assertEquals(listOf("面包"), groups[0].items.map { it.name })
    }

    @Test fun `确认还原清空deletedAt并重排提醒`() = runTest(dispatcher) {
        val g = LocalDateTime.of(2026, 8, 14, 9, 0)
        val a = insertActive("牛奶", g)
        advanceUntilIdle()
        deleteActive(a)

        val deleted = vm.uiState.value.groups[0].items.single { it.name == "牛奶" }
        vm.requestRestore(deleted)
        assertEquals(deleted, vm.uiState.value.restoring)
        vm.cancelRestore()
        assertNull(vm.uiState.value.restoring)

        vm.requestRestore(deleted)
        vm.confirmRestore()
        advanceUntilIdle()
        assertEquals(1, repo.getAll().size)
        assertTrue(scheduler.scheduled.any { it.id == a.id })
        assertNull(vm.uiState.value.restoring)
        assertEquals(UiText(R.string.restored_snackbar, listOf("牛奶")), vm.message.value)
    }

    @Test fun `已过期条目还原不排提醒只取消`() = runTest(dispatcher) {
        val expired = FoodItem(
            name = "酸奶", category = Category.DAIRY,
            productionDate = LocalDate.of(2026, 8, 1), shelfLifeDays = 7,
            quantity = null, createdAt = LocalDateTime.of(2026, 8, 14, 9, 0),
        ).let { item ->
            val id = repo.insert(item)
            repo.items.value.first { it.id == id }
        }
        advanceUntilIdle()
        deleteActive(expired)

        val deleted = vm.uiState.value.groups[0].items.single { it.name == "酸奶" }
        vm.requestRestore(deleted) // 需求-3：随时可还原，无组门禁
        vm.confirmRestore()
        advanceUntilIdle()
        assertEquals(1, repo.getAll().size)
        assertTrue(scheduler.scheduled.isEmpty())
        assertTrue(scheduler.cancelled.contains(expired.id))
    }

    @Test fun `scheduleOrCancel按到期时间分支`() = runTest(dispatcher) {
        val fresh = active("牛奶", now) // 到期 = 今天+7
        scheduler.scheduleOrCancel(fresh, now)
        assertEquals(listOf(fresh), scheduler.scheduled)

        val expired = active("酸奶", LocalDateTime.of(2026, 8, 1, 8, 0)).copy(id = 99) // 到期已过
        scheduler.scheduleOrCancel(expired, now)
        assertTrue(scheduler.cancelled.contains(99L))
    }
}
