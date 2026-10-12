package io.github.chorusfruit233.studywithmiku

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BackgroundSettingsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun manualPermissionAndSystemSettingsAreSeparateActionsAndStatusRefreshes() {
        var exempt by mutableStateOf(false)
        var requests = 0
        var settings = 0
        compose.setContent {
            MikuTheme(Preferences(animateScene = false)) {
                Column(Modifier.padding(16.dp)) {
                    BackgroundSettingsSection(exempt,onRequestPermission = { requests++ },onOpenSettings = { settings++ })
                }
            }
        }
        compose.onNodeWithText("尚未允许忽略系统电池优化").assertIsDisplayed()
        compose.onNodeWithText("获取后台运行权限").performClick()
        compose.runOnIdle { assertEquals(1,requests); assertEquals(0,settings); exempt = true }
        compose.onNodeWithText("已允许忽略系统电池优化").assertIsDisplayed()
        compose.onNodeWithText("打开应用系统设置").performClick()
        compose.runOnIdle { assertEquals(1,requests); assertEquals(1,settings) }
        compose.onNodeWithText("获取后台运行权限").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(2,requests) }
        saveDesignScreenshot("background-access")
    }

    @Test fun unrestrictedGuidanceCanBeDeferredWithoutOpeningSettings() {
        var visible by mutableStateOf(true)
        var opened = 0
        compose.setContent {
            MikuTheme(Preferences(animateScene = false)) {
                if (visible) BatteryGuidanceDialog(onDismiss = { visible = false },onOpenSettings = { opened++; visible = false })
            }
        }
        compose.onNodeWithText("无限制",substring = true).assertIsDisplayed()
        compose.onNodeWithText("稍后").performClick()
        compose.runOnIdle { assertFalse(visible); assertEquals(0,opened); visible = true }
        compose.onNodeWithText("打开系统设置").performClick()
        compose.runOnIdle { assertFalse(visible); assertEquals(1,opened) }
    }
}
