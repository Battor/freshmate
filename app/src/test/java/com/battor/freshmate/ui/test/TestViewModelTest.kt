package com.battor.freshmate.ui.test

import com.battor.freshmate.FakeRepository
import com.battor.freshmate.data.Category
import com.battor.freshmate.data.FoodItem
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TestViewModelTest {
    private val now = LocalDateTime.of(2026, 9, 20, 12, 0)
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeRepository

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeRepository()
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun item(name: String, vararg times: LocalDateTime) = FoodItem(
        name = name,
        category = Category.DAIRY,
        productionDate = LocalDate.of(2026, 9, 20),
        shelfLifeDays = 7,
        quantity = null,
        createdAt = now,
        reminderTimes = times.toList(),
    )

    @Test fun `过滤已过时点只留未来`() = runTest(dispatcher) {
        val past = now.minusHours(1)
        val future = now.plusHours(2)
        repo.items.value = listOf(item("牛奶", past, future))
        val vm = TestViewModel(repo) { now }
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.pending.size)
        assertEquals(future, vm.uiState.value.pending[0].time)
    }

    @Test fun `多条目展平并按时间升序`() = runTest(dispatcher) {
        val a1 = now.plusDays(3)
        val b1 = now.plusDays(1)
        val a2 = now.plusDays(5)
        repo.items.value = listOf(
            item("牛奶", a1, a2),
            item("酸奶", b1),
        )
        val vm = TestViewModel(repo) { now }
        advanceUntilIdle()
        assertEquals(
            listOf(b1 to "酸奶", a1 to "牛奶", a2 to "牛奶"),
            vm.uiState.value.pending.map { it.time to it.itemName },
        )
    }

    @Test fun `空列表与无未来时点都显示空态`() = runTest(dispatcher) {
        repo.items.value = listOf(item("过期面包", now.minusDays(1)))
        val vm = TestViewModel(repo) { now }
        advanceUntilIdle()
        assertEquals(0, vm.uiState.value.pending.size)
        assertEquals(now, vm.uiState.value.now)
    }
}
