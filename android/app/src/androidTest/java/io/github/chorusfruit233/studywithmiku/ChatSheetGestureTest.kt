package io.github.chorusfruit233.studywithmiku

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/** Offline fixture: exercise the production sheet without connecting to the public chat. */
class ChatSheetGestureTest {
    @get:Rule val compose = createComposeRule()

    @Test fun repeatedUpwardSwipesAtLastMessageKeepSheetStillAndHistoryScrollable() {
        var dismissed = false
        lateinit var listState: LazyListState
        compose.setContent {
            MikuTheme(Preferences(animateScene = false)) {
                FocusChatSheet(onDismiss = { dismissed = true }) {
                    val list = rememberLazyListState().also { listState = it }
                    LaunchedEffect(Unit) { list.scrollToItem(49) }
                    LazyColumn(state = list,modifier = Modifier.fillMaxSize().testTag("messages")) {
                        items(50) { index -> Text("Test message $index",Modifier.fillMaxWidth().height(72.dp).testTag("message-$index")) }
                    }
                }
            }
        }
        compose.onNodeWithTag("message-49").assertIsDisplayed()
        val top = compose.onNodeWithTag("messages").fetchSemanticsNode().boundsInRoot.top
        compose.mainClock.autoAdvance = false
        repeat(3) {
            compose.onNodeWithTag("messages").performTouchInput { swipeUp(durationMillis = 120) }
            repeat(45) {
                compose.mainClock.advanceTimeByFrame()
                val currentTop = compose.onNodeWithTag("messages").fetchSemanticsNode().boundsInRoot.top
                assertTrue("Sheet moved at the list's bottom: $top -> $currentTop",abs(currentTop-top) <= 1f)
            }
        }
        assertFalse("Reaching the last message must not dismiss the sheet",dismissed)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("messages").performTouchInput { swipeDown(durationMillis = 300) }
        compose.runOnIdle { assertTrue("Older messages must remain scrollable",listState.canScrollForward) }
    }

    @Test fun downwardSwipeAtFirstMessageStillDismissesSheet() {
        var dismissed = false
        compose.setContent {
            MikuTheme(Preferences(animateScene = false)) {
                FocusChatSheet(onDismiss = { dismissed = true }) {
                    LazyColumn(Modifier.fillMaxSize().testTag("messages")) {
                        items(50) { Text("Test message $it",Modifier.height(72.dp)) }
                    }
                }
            }
        }
        compose.onNodeWithTag("messages").performTouchInput { swipeDown(durationMillis = 120) }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue("Downward dragging at the top must still dismiss",dismissed) }
    }
}
