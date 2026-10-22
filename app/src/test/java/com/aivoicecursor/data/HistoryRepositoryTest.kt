package com.aivoicecursor.data

import com.aivoicecursor.core.ActionPlan
import com.aivoicecursor.core.ActionType
import com.aivoicecursor.core.AgentAction
import com.aivoicecursor.core.ExecutionResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryRepositoryTest {
    private class FakeDao : HistoryDao {
        val state = MutableStateFlow<List<HistoryEntity>>(emptyList())
        override suspend fun insert(entity: HistoryEntity): Long {
            val id = state.value.size + 1L
            state.value = listOf(entity.copy(id = id)) + state.value
            return id
        }
        override fun observe(): Flow<List<HistoryEntity>> = state
        override suspend fun clear() { state.value = emptyList() }
    }

    @Test fun recordsNewestFirstAndClears() = runTest {
        var now = 1_000L
        val repo = HistoryRepository(FakeDao()) { now++ }
        val plan = ActionPlan(listOf(AgentAction(ActionType.CLICK, target = "Search")))
        repo.record("first", plan, ExecutionResult(true, "ok"))
        repo.record("second", null, ExecutionResult(false, "failed"))

        val list = repo.entries.first()
        assertEquals(listOf("second", "first"), list.map { it.command })
        assertTrue(list[1].success && list[1].planJson.contains("CLICK"))
        assertFalse(list[0].success)
        assertEquals("", list[0].planJson)

        repo.clear()
        assertTrue(repo.entries.first().isEmpty())
    }
}
