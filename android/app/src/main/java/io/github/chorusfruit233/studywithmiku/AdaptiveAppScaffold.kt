package io.github.chorusfruit233.studywithmiku

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Window constraints also cover split-screen and freeform windows; no orientation lock. */
@Composable internal fun AdaptiveAppScaffold(
    tab: Int,
    onTabChange: (Int) -> Unit,
    snackbar: SnackbarHostState,
    title: @Composable RowScope.() -> Unit,
    actions: @Composable RowScope.() -> Unit,
    miniPlayer: (@Composable (compact: Boolean) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val icons = listOf(Icons.Rounded.Timer,Icons.Rounded.Headphones,Icons.Rounded.Checklist,Icons.Rounded.BarChart,Icons.Rounded.Forum)
    val labels = listOf("专注","音乐","计划","记录","社区")
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
        val rail = maxWidth >= 600.dp && (maxWidth > maxHeight || maxWidth >= 840.dp)
        val compact = maxHeight < 480.dp
        CompositionLocalProvider(LocalCompactHeight provides compact) {
            Row(Modifier.fillMaxSize()) {
                if (rail) Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.width(80.dp).fillMaxHeight().testTag("navigation-rail")
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))
                        .verticalScroll(rememberScrollState()).selectableGroup().padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),horizontalAlignment = Alignment.CenterHorizontally) {
                        labels.forEachIndexed { i,label ->
                            NavigationRailItem(selected = tab == i,onClick = { onTabChange(i) },
                                icon = { Icon(icons[i],label) },
                                label = if (compact) null else { { Text(label) } },
                                modifier = Modifier.testTag("destination-$i"))
                        }
                    }
                }
                Scaffold(modifier = Modifier.weight(1f).imePadding(),containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical),
                    topBar = {
                        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                            .padding(horizontal = if (rail) 12.dp else 24.dp,vertical = if (compact) 2.dp else 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            if (rail && miniPlayer != null) Box(Modifier.weight(1f)) { miniPlayer(true) }
                            else title()
                            actions()
                        }
                    },
                    snackbarHost = { SnackbarHost(snackbar) },
                    bottomBar = {
                        if (!rail) Column {
                            miniPlayer?.invoke(false)
                            NavigationBar(modifier = Modifier.testTag("navigation-bar"),
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,tonalElevation = 0.dp) {
                                labels.forEachIndexed { i,label ->
                                    NavigationBarItem(selected = tab == i,onClick = { onTabChange(i) },
                                        icon = { Icon(icons[i],label) },
                                        label = { Text(label,fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Medium) },
                                        modifier = Modifier.testTag("destination-$i"))
                                }
                            }
                        }
                    },
                ) { insets ->
                    Box(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets),contentAlignment = Alignment.TopCenter) {
                        Box(Modifier.widthIn(max = 1200.dp).fillMaxSize(),content = { content() })
                    }
                }
            }
        }
    }
}
