package io.github.chorusfruit233.studywithmiku

import org.junit.Assert.*
import org.junit.Test

class FocusClockTest {
    private val prefs = Preferences(focusMinutes = 1,breakMinutes = 1,longBreakMinutes = 3)
    @Test fun backgroundDelayCreditsOnlySessionDuration() {
        val c = FocusClock(remainingMs = 60_000).start(100)
        val result = c.tick(600_100,prefs)
        assertEquals(60_000L,result.studiedMs)
        assertTrue(result.completedFocus)
        assertTrue(result.finished)
        assertFalse(result.clock.running)
        assertEquals(Phase.BREAK,result.clock.phase)
        assertEquals(1,result.clock.round)
    }
    @Test fun pausedTimeIsExcludedAndResumeUsesNewAnchor() {
        val first = FocusClock(remainingMs = 60_000).start(100).tick(10_100,prefs)
        val paused = first.clock.copy(running = false)
        assertEquals(0L,paused.tick(100_000,prefs).studiedMs)
        val resumed = paused.start(100_000).tick(105_000,prefs)
        assertEquals(5_000L,resumed.studiedMs)
        assertEquals(45_000L,resumed.clock.remainingMs)
    }
    @Test fun everyFourthFocusHasLongBreak() {
        val result = FocusClock(remainingMs = 1_000,round = 3).start(0).tick(1_000,prefs)
        assertEquals(Phase.LONG_BREAK,result.clock.phase)
        assertEquals(180_000L,result.clock.remainingMs)
    }
    @Test fun breaksDoNotIncreaseStudyTimeOrCompletedTomatoes() {
        val result = FocusClock(phase = Phase.BREAK,remainingMs = 60_000).start(0).tick(60_000,prefs)
        assertEquals(0L,result.studiedMs)
        assertFalse(result.completedFocus)
        assertEquals(Phase.FOCUS,result.clock.phase)
    }
    @Test fun stopwatchAccumulatesAcrossLongBackgroundInterval() {
        val result = FocusClock(mode = TimerMode.STOPWATCH,remainingMs = 0).start(0).tick(7_200_000,prefs)
        assertEquals(7_200_000L,result.studiedMs)
        assertEquals(7_200_000L,result.clock.elapsedMs)
        assertFalse(result.finished)
        assertTrue(result.clock.running)
    }
    @Test fun countdownDoesNotCountAsAPomodoro() {
        val result = FocusClock(mode = TimerMode.COUNTDOWN,remainingMs = 60_000).start(0).tick(60_000,prefs)
        assertEquals(60_000L,result.studiedMs)
        assertFalse(result.completedFocus)
        assertEquals(Phase.FOCUS,result.clock.phase)
        assertFalse(result.clock.running)
    }
    @Test fun completedCountdownStartsANewFullSession() {
        val countdownPrefs = prefs.copy(countdownMinutes = 1,countdownSeconds = 60)
        val finished = FocusClock(mode = TimerMode.COUNTDOWN,remainingMs = 60_000).start(100).tick(60_100,countdownPrefs)
        assertTrue(finished.finished)
        assertEquals(60_000L,finished.studiedMs)
        assertEquals(60_000L,finished.clock.remainingMs)
        assertEquals(0L,finished.clock.elapsedMs)

        val restarted = finished.clock.start(90_100).tick(95_100,countdownPrefs)
        assertTrue(restarted.clock.running)
        assertFalse(restarted.finished)
        assertEquals(5_000L,restarted.studiedMs)
        assertEquals(55_000L,restarted.clock.remainingMs)
    }
    @Test fun repeatedTickDoesNotDuplicateCredit() {
        val first = FocusClock(remainingMs = 60_000).start(0).tick(10_000,prefs)
        assertEquals(0L,first.clock.tick(10_000,prefs).studiedMs)
    }
}
