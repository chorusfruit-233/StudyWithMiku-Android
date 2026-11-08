package io.github.chorusfruit233.studywithmiku

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp

internal val LocalCompactHeight = compositionLocalOf { false }

@Composable internal fun adaptiveSheetMaxWidth(): androidx.compose.ui.unit.Dp {
    val size = LocalWindowInfo.current.containerSize
    val width = with(LocalDensity.current) { size.width.toDp() }
    return if (size.width > size.height && width >= 600.dp) 880.dp else 640.dp
}

/** Keep the same composition slots when resizing, so edits and scroll state survive rotation. */
@Composable internal fun AdaptivePaneLayout(
    primary: @Composable ColumnScope.() -> Unit,
    secondary: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        val twoPanes = maxWidth >= 520.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
        val pageScroll = rememberScrollState()
        val primaryScroll = rememberScrollState()
        val secondaryScroll = rememberScrollState()
        Layout(
            modifier = Modifier.fillMaxSize().then(if (twoPanes) Modifier else Modifier.verticalScroll(pageScroll)),
            content = {
                Column(Modifier.then(if (twoPanes) Modifier.verticalScroll(primaryScroll) else Modifier).testTag("primary-pane"),
                    verticalArrangement = Arrangement.spacedBy(16.dp),content = primary)
                Column(Modifier.then(if (twoPanes) Modifier.verticalScroll(secondaryScroll) else Modifier).testTag("secondary-pane"),
                    verticalArrangement = Arrangement.spacedBy(16.dp),content = secondary)
            },
        ) { measurables,constraints ->
            val gap = 20.dp.roundToPx()
            val width = if (twoPanes) (constraints.maxWidth-gap)/2 else constraints.maxWidth
            val childConstraints = Constraints(minWidth = width,maxWidth = width,
                maxHeight = if (twoPanes) constraints.maxHeight else Constraints.Infinity)
            val first = measurables[0].measure(childConstraints)
            val second = measurables[1].measure(childConstraints)
            val height = if (twoPanes) maxOf(first.height,second.height) else first.height+16.dp.roundToPx()+second.height
            layout(constraints.maxWidth,constraints.constrainHeight(height)) {
                first.placeRelative(0,0)
                second.placeRelative(if (twoPanes) width+gap else 0,if (twoPanes) 0 else first.height+16.dp.roundToPx())
            }
        }
    }
}
