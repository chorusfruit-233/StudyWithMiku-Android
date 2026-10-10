package io.github.chorusfruit233.studywithmiku

import org.junit.Assert.assertEquals
import org.junit.Test

class FocusPresentationTest {
    @Test fun pausedBreakContinuesRestInsteadOfFocus() {
        val paused = FocusClock(phase = Phase.BREAK,elapsedMs = 30_000,remainingMs = 270_000)
        assertEquals("短休息 · 已暂停",focusStatusLabel(paused))
        assertEquals("继续休息",focusActionLabel(paused))
        assertEquals("开始休息",focusActionLabel(paused.copy(elapsedMs = 0)))
    }

    @Test fun pausedCountingModesAreNotPresentedAsReadyToStart() {
        listOf(TimerMode.COUNTDOWN,TimerMode.STOPWATCH).forEach { mode ->
            val clock = FocusClock(mode = mode,elapsedMs = 30_000)
            assertEquals("已暂停",focusStatusLabel(clock))
            assertEquals("继续专注",focusActionLabel(clock))
            assertEquals("准备开始",focusStatusLabel(clock.copy(elapsedMs = 0)))
        }
    }

    @Test fun completedFocusKeepsItsRoundAndActualLongBreakDuration() {
        val prefs = Preferences(focusSeconds = 1500,breakSeconds = 300,longBreakSeconds = 1200)
        val finished = FocusClock(remainingMs = 1000,round = 3).start(0).tick(1000,prefs)
        assertEquals("第 04 轮 · 长休息 20 分钟",focusDetailLabel(finished.clock,prefs))
        assertEquals("第 05 轮 · 专注 25 分钟",focusDetailLabel(finished.clock.copy(phase = Phase.FOCUS),prefs))
    }

    @Test fun fractionalMinutesStayReadableAndWholeMinutesHaveNoDecimalSuffix() {
        assertEquals("第 01 轮 · 专注 25 分钟",focusDetailLabel(FocusClock(),Preferences()))
        assertEquals("第 01 轮 · 专注 1.17 分钟",focusDetailLabel(FocusClock(),Preferences(focusSeconds = 70)))
        assertEquals("第 01 轮 · 专注 0.1 分钟",focusDetailLabel(FocusClock(),Preferences(focusSeconds = 6)))
    }
}
