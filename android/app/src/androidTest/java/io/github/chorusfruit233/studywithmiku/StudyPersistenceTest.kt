package io.github.chorusfruit233.studywithmiku

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

/** Real Android SharedPreferences and native data projection, with isolated synthetic profiles. */
class StudyPersistenceTest {
    private fun isolated(test: (Context) -> Unit) {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "sync_fixture_${UUID.randomUUID()}"
        val context = object : ContextWrapper(target) {
            override fun getSharedPreferences(ignored: String,mode: Int) = target.getSharedPreferences(name,mode)
        }
        try { test(context) } finally { target.deleteSharedPreferences(name) }
    }
    private fun remote(): CloudStudy {
        val today = LocalDate.now()
        return CloudStudy(JSONObject("""{
            "stats":{"totalStudyTime":2400.5,"totalPomodoros":4,"todayStudyTime":600.5,"todayPomodoros":1,"lastDate":"${StudyProtocol.jsDate(today)}"},
            "todos":[{"id":123,"text":"Cloud task","completed":true,"pinned":true}],
            "settings":{"focusDuration":40,"breakDuration":8,"countdowns":[{"id":7,"title":"Exam","date":"2027-01-01"}]},
            "futureField":"preserved"
        }"""),JSONObject("""{
            "dailyLog":{"$today":{"studyTime":600.5,"pomodoros":1}},
            "plans":{"$today":[{"id":456,"text":"Cloud plan","done":true,"color":"#39c5bb"}]}
        }"""))
    }
    @Test fun cloudStatsTasksCalendarAndSettingsSurviveStoreRecreation() = isolated { context ->
        val remote = remote()
        val store = MikuStore(context)
        store.switchStudyOwner("fixture-account")
        store.applyStudy(remote)
        store.rememberAppliedStudy(store.studySnapshot())
        val reopened = MikuStore(context)
        assertEquals("fixture-account",reopened.studyOwner)
        assertEquals(2_400_500L,reopened.studyTotals().millis)
        assertEquals(4,reopened.studyTotals().sessions)
        assertEquals(600_500L,reopened.logs.value[LocalDate.now().toString()]!!.millis)
        assertEquals(2,reopened.todos.value.size)
        assertTrue(reopened.todos.value.all { it.done })
        assertEquals(2400,reopened.prefs.value.focusSeconds)
        assertEquals(480,reopened.prefs.value.breakSeconds)
        assertEquals("Exam",reopened.countdowns.value.single().title)
        assertTrue(StudyProtocol.sameStudy(remote,reopened.lastCloudStudy()!!))
        assertTrue(StudyProtocol.sameLocalStudy(reopened.studySnapshot(),reopened.lastAppliedStudy()!!))
        assertEquals("preserved",reopened.studySnapshot().data.getString("futureField"))
    }
    @Test fun acceptedSnapshotIsBackedUpAndRestoredForEachAccount() = isolated { context ->
        val store = MikuStore(context)
        store.switchStudyOwner("fixture-a"); store.applyStudy(remote())
        val accepted = store.studySnapshot(); store.rememberAppliedStudy(accepted,pendingUpload = true)
        store.switchStudyOwner("fixture-b")
        assertNull(store.lastCloudStudy()); assertNull(store.lastAppliedStudy())
        assertFalse(store.pendingStudyUpload)
        assertEquals(0L,store.studyTotals().millis)
        store.switchStudyOwner("")
        assertNull(store.lastAppliedStudy())
        store.switchStudyOwner("fixture-a")
        val reopened = MikuStore(context)
        assertEquals("fixture-a",reopened.studyOwner)
        assertTrue(StudyProtocol.sameLocalStudy(accepted,reopened.lastAppliedStudy()!!))
        assertTrue(reopened.pendingStudyUpload)
        assertEquals(2_400_500L,reopened.studyTotals().millis)
    }
    @Test fun decimalCreditAndUnknownItemFieldsSurviveNativeRoundTrip() = isolated { context ->
        val remote = remote()
        remote.data.getJSONObject("stats").put("totalStudyTime",2400.501)
        remote.data.getJSONObject("settings").put("focusDuration",6.1)
        remote.data.getJSONArray("todos").getJSONObject(0).put("futureTodo","keep")
        remote.data.getJSONObject("settings").getJSONArray("countdowns").getJSONObject(0).put("futureCountdown","keep")
        remote.calendar.getJSONObject("dailyLog").getJSONObject(LocalDate.now().toString()).put("futureDay","keep")
        remote.calendar.getJSONObject("plans").getJSONArray(LocalDate.now().toString()).getJSONObject(0).put("futurePlan","keep")
        val store = MikuStore(context)
        store.switchStudyOwner("fixture-account"); store.applyStudy(remote)
        val reopened = MikuStore(context)
        val snapshot = reopened.studySnapshot()
        assertEquals(2_400_501L,reopened.studyTotals().millis)
        assertEquals(366,reopened.prefs.value.focusSeconds)
        assertEquals("keep",snapshot.data.getJSONArray("todos").getJSONObject(0).getString("futureTodo"))
        assertEquals("keep",snapshot.data.getJSONObject("settings").getJSONArray("countdowns").getJSONObject(0).getString("futureCountdown"))
        assertEquals("keep",snapshot.calendar.getJSONObject("dailyLog").getJSONObject(LocalDate.now().toString()).getString("futureDay"))
        assertEquals("keep",snapshot.calendar.getJSONObject("plans").getJSONArray(LocalDate.now().toString()).getJSONObject(0).getString("futurePlan"))
    }

    @Test fun nonFiniteLegacySettingsCannotPoisonNativePreferenceStorage() = isolated { context ->
        val remote = remote()
        remote.data.getJSONObject("settings").put("focusDuration","NaN").put("breakDuration","Infinity")
            .put("countdownDuration","NaN").put("notificationVolume","NaN")
        val store = MikuStore(context); val before = store.prefs.value
        store.switchStudyOwner("fixture-account"); store.applyStudy(remote)
        val reopened = MikuStore(context)
        assertEquals(before.focusSeconds,reopened.prefs.value.focusSeconds)
        assertEquals(before.breakSeconds,reopened.prefs.value.breakSeconds)
        assertEquals(before.countdownSeconds,reopened.prefs.value.countdownSeconds)
        assertEquals(0.6f,reopened.prefs.value.notificationVolume,0.0f)
        assertEquals(2_400_500L,reopened.studyTotals().millis)
        assertTrue(reopened.prefs.value.notificationVolume.isFinite())
    }
    @Test fun applyingUploadedMergePreservesRunningClockAndRevisionAcrossRecreation() = isolated { context ->
        val store = MikuStore(context)
        store.switchStudyOwner("fixture-account"); store.applyStudy(remote())
        val clock = FocusClock(running = true,remainingMs = 123000,lastTickMs = 1000,elapsedMs = 45000)
        store.saveClock(clock,42)
        val before = context.getSharedPreferences("fixture",0).getString("clock",null)
        assertNotNull(before)
        val saved = remote().also { it.data.put("revision",7); it.data.getJSONObject("stats").put("totalStudyTime",2500.5) }
        store.applyUploadedStudy(saved)
        val reopened = MikuStore(context)
        assertEquals(before,context.getSharedPreferences("fixture",0).getString("clock",null))
        assertEquals(7,reopened.lastCloudStudy()!!.data.getInt("revision"))
        assertEquals(2_500_500L,reopened.studyTotals().millis)
    }

    @Test fun mergedTodayCreditSchedulesCalendarFollowupWithoutPausingRunningTimer() = isolated { context ->
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val today = LocalDate.now()
            fun document(time: Int,revision: Int) = CloudStudy(JSONObject().put("revision",revision)
                .put("stats",JSONObject().put("totalStudyTime",time).put("todayStudyTime",time).put("lastDate",StudyProtocol.jsDate(today))),
                JSONObject().put("dailyLog",JSONObject().put(today.toString(),JSONObject().put("studyTime",time))))
            val base = document(100,1)
            val store = MikuStore(context).apply { autoSync = false; switchStudyOwner("fixture"); applyStudy(base) }
            var pauses = 0
            val timer = object : StudySyncTimer {
                override val state = kotlinx.coroutines.flow.MutableStateFlow(FocusClock(running = true))
                override fun pause() { pauses++ }
                override fun flush() {}
                override fun restoreFromStore() {}
            }
            val account = object : StudySyncAccount {
                override val state = kotlinx.coroutines.flow.MutableStateFlow(AccountState("fixture","Miku",Long.MAX_VALUE))
                override fun token() = "fixture-token"
                override fun expire() {}
                override fun logout() {}
            }
            var writes = 0
            val api = object : StudySyncApi {
                override fun fetch(token: String) = base
                override fun push(token: String,study: CloudStudy) { error("Revision upload expected") }
                override fun push(token: String,study: CloudStudy,baseline: CloudStudy?): CloudStudy {
                    writes++
                    return if (writes == 1) CloudStudy(document(190,2).data,study.calendar)
                    else {
                        assertEquals(190.0,study.calendar.getJSONObject("dailyLog").getJSONObject(today.toString()).getDouble("studyTime"),0.0)
                        study.copy(data = StudyProtocol.deepCopy(study.data).put("revision",3))
                    }
                }
            }
            val sync = CloudSync(store,timer,account,api,kotlinx.coroutines.Dispatchers.Main.immediate)
            try {
                sync.start(); assertTrue(sync.state.value.ready)
                val before = pauses
                store.record(60_000,false)
                sync.syncNow()
                assertTrue(store.pendingStudyUpload); assertEquals(190_000L,store.studyTotals().millis)
                sync.syncNow()
                assertFalse(store.pendingStudyUpload); assertEquals(2,writes)
                assertEquals(before,pauses); assertTrue(timer.state.value.running)
            } finally { sync.close() }
        }
    }

}
