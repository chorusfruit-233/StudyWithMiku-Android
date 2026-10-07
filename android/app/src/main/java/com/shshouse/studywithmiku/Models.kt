package com.shshouse.studywithmiku

enum class TimerMode(val label: String) { POMODORO("番茄钟"), COUNTDOWN("倒计时"), STOPWATCH("正计时") }
enum class Phase(val label: String) { FOCUS("专注"), BREAK("短休息"), LONG_BREAK("长休息") }
data class Preferences(
    val focusMinutes: Int = 25, val breakMinutes: Int = 5, val longBreakMinutes: Int = 15,
    val countdownMinutes: Int = 30, val dailyGoalMinutes: Int = 120,
    val scene: Int = 0, val theme: Int = 0, val dynamicColor: Boolean = false,
    val animateScene: Boolean = true, val keepScreenOn: Boolean = false,
    val completionSound: Boolean = true
)
data class Todo(val id: String, val text: String, val date: String, val done: Boolean = false)
data class DayLog(val millis: Long = 0, val sessions: Int = 0)
data class Song(val id: String, val title: String, val artist: String, val uri: String,
                val cover: String = "", val builtin: Boolean = false)
data class Playlist(val id: String, val name: String, val songIds: List<String>)
data class Library(val songs: List<Song> = emptyList(), val favorites: Set<String> = emptySet(),
                   val playlists: List<Playlist> = emptyList())

/** Pure monotonic clock model. Wall clock changes cannot shorten a running session. */
data class FocusClock(
    val mode: TimerMode = TimerMode.POMODORO, val phase: Phase = Phase.FOCUS,
    val running: Boolean = false, val remainingMs: Long = 25 * 60_000L,
    val elapsedMs: Long = 0, val lastTickMs: Long = 0, val round: Int = 0
) {
    fun start(now: Long) = copy(running = true, lastTickMs = now)
    fun tick(now: Long, prefs: Preferences): ClockTick {
        if (!running) return ClockTick(this)
        val delta = (now - lastTickMs).coerceAtLeast(0)
        val used = if (mode == TimerMode.STOPWATCH) delta else delta.coerceAtMost(remainingMs)
        val credit = if (phase == Phase.FOCUS) used else 0L
        val updated = copy(remainingMs = (remainingMs - used).coerceAtLeast(0),
            elapsedMs = elapsedMs + used, lastTickMs = now)
        if (mode == TimerMode.STOPWATCH || updated.remainingMs > 0) return ClockTick(updated, credit)
        val completed = mode == TimerMode.POMODORO && phase == Phase.FOCUS
        val rounds = round + if (completed) 1 else 0
        val nextPhase = if (mode != TimerMode.POMODORO) Phase.FOCUS
            else if (phase != Phase.FOCUS) Phase.FOCUS
            else if (rounds % 4 == 0) Phase.LONG_BREAK else Phase.BREAK
        return ClockTick(updated.copy(running = false, phase = nextPhase, round = rounds,
            remainingMs = duration(mode, nextPhase, prefs), elapsedMs = 0), credit, completed, true)
    }
    companion object {
        fun duration(mode: TimerMode, phase: Phase, prefs: Preferences): Long = when {
            mode == TimerMode.STOPWATCH -> 0L
            mode == TimerMode.COUNTDOWN -> prefs.countdownMinutes * 60_000L
            phase == Phase.BREAK -> prefs.breakMinutes * 60_000L
            phase == Phase.LONG_BREAK -> prefs.longBreakMinutes * 60_000L
            else -> prefs.focusMinutes * 60_000L
        }
    }
}
data class ClockTick(val clock: FocusClock, val studiedMs: Long = 0,
                     val completedFocus: Boolean = false, val finished: Boolean = false)

fun timeLabel(millis: Long): String {
    val seconds = (millis.coerceAtLeast(0) + 999) / 1000
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    else "%02d:%02d".format(seconds / 60, seconds % 60)
}
