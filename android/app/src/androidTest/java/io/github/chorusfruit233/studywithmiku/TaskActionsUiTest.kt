package io.github.chorusfruit233.studywithmiku

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

/** Click production task controls in a blank Compose activity using an isolated local store. */
class TaskActionsUiTest {
    @get:Rule val compose = createComposeRule()

    private fun isolated(test: (Context,MikuStore) -> Unit) {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "task_actions_fixture_${UUID.randomUUID()}"
        val context = object : ContextWrapper(target) {
            override fun getSharedPreferences(ignored: String,mode: Int) = target.getSharedPreferences(name,mode)
        }
        try { test(context,MikuStore(context)) } finally { target.deleteSharedPreferences(name) }
    }

    private fun showTasks(store: MikuStore) {
        compose.setContent {
            val todos by store.todos.collectAsState()
            MikuTheme(Preferences(animateScene = false)) { TasksScreen(todos,store,onShare = {}) }
        }
    }

    @Test fun editingGlobalTaskUpdatesSameUndatedTaskWithoutAddingAnother() = isolated { context,store ->
        store.addTodo("Original undated target")
        val original = store.todos.value.single()
        showTasks(store)
        compose.onNodeWithText(original.text).performScrollTo().performClick()
        compose.onNodeWithText("编辑小目标").assertIsDisplayed()
        saveDesignScreenshot("task-edit")
        val today = LocalDate.now()
        compose.onNodeWithText("${today.year} 年 ${today.monthValue} 月 ${today.dayOfMonth} 日",substring = true)
            .assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performTextReplacement("Updated undated target")
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle {
            val updated = store.todos.value.single()
            assertEquals(original.id,updated.id)
            assertEquals("Updated undated target",updated.text)
            assertEquals("",updated.date)
            assertEquals(original.color,updated.color)
            assertEquals(updated,MikuStore(context).todos.value.single())
        }
    }

    @Test fun deletingTaskRequiresConfirmationAndKeepsStudyCredit() = isolated { context,store ->
        store.addTodo("Target to remove")
        store.record(60_000,false)
        val original = store.todos.value.single()
        val originalLogs = store.logs.value.toMap()
        showTasks(store)
        compose.onNodeWithContentDescription("删除待办").performScrollTo().performClick()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertEquals(listOf(original),store.todos.value) }
        compose.onNodeWithContentDescription("删除待办").performClick()
        compose.onNodeWithText("删除").performClick()
        compose.runOnIdle {
            assertTrue(store.todos.value.isEmpty())
            assertTrue(MikuStore(context).todos.value.isEmpty())
            assertEquals(originalLogs,store.logs.value)
            assertEquals(60_000L,store.studyTotals().millis)
        }
    }

    @Test fun deletingCountdownRequiresConfirmationAndKeepsOtherTasks() = isolated { context,store ->
        store.addTodo("Keep this task")
        store.addCountdown("Exam","2027-06-01")
        val originalTask = store.todos.value.single()
        val originalCountdown = store.countdowns.value.single()
        showTasks(store)
        compose.onNodeWithContentDescription("删除倒数日").performScrollTo().performClick()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertEquals(listOf(originalCountdown),store.countdowns.value) }
        compose.onNodeWithContentDescription("删除倒数日").performClick()
        compose.onNodeWithText("删除").performClick()
        compose.runOnIdle {
            assertTrue(store.countdowns.value.isEmpty())
            val reopened = MikuStore(context)
            assertTrue(reopened.countdowns.value.isEmpty())
            assertEquals(listOf(originalTask),reopened.todos.value)
        }
    }
}
