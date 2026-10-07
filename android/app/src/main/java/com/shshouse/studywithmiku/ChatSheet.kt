@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.shshouse.studywithmiku

import androidx.compose.foundation.layout.*
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

/** Keep upward edge gestures inside the chat; downward gestures still reach the sheet. */
internal object ChatSheetScrollBoundary : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset,available: Offset,source: NestedScrollSource) =
        Offset(0f,available.y.coerceAtMost(0f))

    override suspend fun onPostFling(consumed: Velocity,available: Velocity) =
        Velocity(0f,available.y.coerceAtMost(0f))
}

@Composable fun FocusChatSheet(onDismiss: () -> Unit,content: @Composable () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss,sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Box(Modifier.fillMaxWidth().heightIn(min = 300.dp,max = 620.dp)
            .nestedScroll(ChatSheetScrollBoundary).padding(16.dp)) { content() }
    }
}
