@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package io.github.chorusfruit233.studywithmiku

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException

class CloudSyncTest {
    private val dispatcher = StandardTestDispatcher()
    private val coordinators = mutableListOf<CloudSync>()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { coordinators.forEach { it.close() }; Dispatchers.resetMain() }

    private fun scenario(block: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try { block() } finally { coordinators.forEach { it.close() } }
    }

    private fun study(seconds: Int,text: String = "cloud") = CloudStudy(JSONObject("""{
        "stats":{"totalStudyTime":$seconds,"totalPomodoros":1},
        "todos":[{"id":123,"text":"$text","completed":false}],
        "settings":{"focusDuration":40}
    }"""),JSONObject("""{"dailyLog":{"2026-10-07":{"studyTime":$seconds,"pomodoros":1}},"plans":{}}"""))
    private fun copy(study: CloudStudy) = CloudStudy(StudyProtocol.deepCopy(study.data),StudyProtocol.deepCopy(study.calendar))
    private inner class Store(initial: CloudStudy = study(30,"local")) : StudySyncStore {
        override val revision = MutableStateFlow(0L)
        override var studyOwner = "u"
        override var autoSync = false
        override var pendingStudyUpload = false
        var local = copy(initial)
        var cloud: CloudStudy? = null
        var applied: CloudStudy? = null
        var imports = 0
        override fun studySnapshot() = copy(local)
        override fun lastCloudStudy() = cloud?.let(::copy)
        override fun lastAppliedStudy() = applied?.let(::copy)
        override fun rememberAppliedStudy(study: CloudStudy,pendingUpload: Boolean) { applied = copy(study); pendingStudyUpload = pendingUpload }
        override fun switchStudyOwner(id: String) { studyOwner = id }
        override fun applyStudy(study: CloudStudy) { local = copy(study); rememberCloud(study); imports++; revision.value++ }
        override fun rememberCloud(study: CloudStudy) { cloud = copy(study) }
    }
    private class Timer : StudySyncTimer {
        override val state = MutableStateFlow(FocusClock())
        override fun pause() {}
        override fun flush() {}
        override fun restoreFromStore() {}
    }
    private class Account : StudySyncAccount {
        override val state = MutableStateFlow(AccountState("u","Miku",Long.MAX_VALUE))
        override fun token() = if (state.value.loggedIn) "fixture-token" else ""
        override fun expire() { state.value = state.value.copy(expired = true) }
        override fun logout() { state.value = AccountState() }
    }
    private inner class Api(var remote: CloudStudy = study(120)) : StudySyncApi {
        var reads = 0
        var writes = 0
        var error: Exception? = null
        var onRead: (() -> Unit)? = null
        override fun fetch(token: String): CloudStudy {
            reads++; onRead?.invoke(); error?.let { throw it }; return copy(remote)
        }
        override fun push(token: String,study: CloudStudy) { writes++; error?.let { throw it }; remote = copy(study) }
    }
    private fun coordinator(store: Store,api: Api,account: Account = Account()) =
        CloudSync(store,Timer(),account,api,dispatcher).also { coordinators.add(it); it.start() }

    @Test fun cloudChoiceFetchesLatestDataSavesLocallyAndDoesNotUpload() = scenario {
        val store = Store(); val api = Api(); val sync = coordinator(store,api)
        runCurrent(); assertNotNull(sync.state.value.conflict)
        api.remote = study(240,"latest cloud")
        sync.resolve(1)
        assertTrue(sync.state.value.busy); assertNotNull(sync.state.value.conflict)
        runCurrent()
        assertTrue(StudyProtocol.sameStudy(api.remote,store.local))
        assertTrue(StudyProtocol.sameStudy(store.local,store.applied!!))
        assertEquals(2,api.reads); assertEquals(0,api.writes)
        assertTrue(sync.state.value.ready); assertFalse(sync.state.value.busy)
        assertNull(sync.state.value.conflict); assertEquals("学习资料已保存到本机",sync.state.value.message)
    }
    @Test fun restartingAfterCloudChoiceDoesNotAskAgain() = scenario {
        val store = Store(); val api = Api(); val first = coordinator(store,api)
        runCurrent(); first.resolve(1); runCurrent(); first.close()
        val restarted = coordinator(store,api); runCurrent()
        assertNull(restarted.state.value.conflict); assertTrue(restarted.state.value.ready)
        assertEquals(1,store.imports); assertEquals(0,api.writes)
    }
    @Test fun existing121AcceptedCloudSnapshotDoesNotRepeatDialog() = scenario {
        val api = Api(); val store = Store(api.remote)
        store.cloud = copy(api.remote) // Older versions stored this but no native baseline.
        val sync = coordinator(store,api); runCurrent()
        assertNull(sync.state.value.conflict); assertTrue(sync.state.value.ready)
        assertTrue(StudyProtocol.sameStudy(api.remote,store.local)); assertEquals(0,api.writes)
    }
    @Test fun newerCloudIsAdoptedWhenNativeSnapshotHasNotChanged() = scenario {
        val api = Api(); val store = Store(api.remote)
        store.cloud = copy(api.remote); store.applied = copy(store.local)
        api.remote = study(300,"edited on web")
        val sync = coordinator(store,api); runCurrent()
        assertTrue(StudyProtocol.sameStudy(api.remote,store.local))
        assertNull(sync.state.value.conflict); assertEquals(0,api.writes)
    }
    @Test fun nativeSettingsProjectionDoesNotCauseFalseConflictAfterRestart() = scenario {
        val api = Api(); val store = Store(api.remote)
        store.local.data.getJSONObject("settings").put("breakDuration",5.0)
        store.cloud = copy(api.remote); store.applied = copy(store.local)
        api.remote = study(300)
        val sync = coordinator(store,api); runCurrent()
        assertNull(sync.state.value.conflict); assertEquals(300,store.local.data.getJSONObject("stats").getInt("totalStudyTime"))
    }
    @Test fun unchangedCloudPreservesUnuploadedLocalChanges() = scenario {
        val api = Api(); val store = Store(api.remote)
        store.cloud = copy(api.remote); store.applied = copy(store.local)
        store.local = study(240,"local offline edit")
        val expected = copy(store.local)
        val sync = coordinator(store,api); runCurrent()
        assertTrue(StudyProtocol.sameStudy(expected,store.local)); assertNull(sync.state.value.conflict)
        assertEquals(0,store.imports); assertEquals(0,api.writes)
    }
    @Test fun changesOnBothSidesStillRequireChoiceAndBlockAutomaticUpload() = scenario {
        val api = Api(); val store = Store(api.remote)
        store.cloud = copy(api.remote); store.applied = copy(store.local)
        store.local = study(240,"local"); api.remote = study(300,"remote"); store.autoSync = true
        val sync = coordinator(store,api); runCurrent(); advanceTimeBy(2001); runCurrent()
        assertNotNull(sync.state.value.conflict); assertEquals(0,api.writes)
        sync.syncNow(); runCurrent(); assertNotNull(sync.state.value.conflict); assertEquals(0,api.writes)
    }
    @Test fun postponingChoicePreservesLocalDataAndBlocksUploadsUntilChosen() = scenario {
        val store = Store().apply { autoSync = true }; val before = copy(store.local)
        val api = Api(); val sync = coordinator(store,api)
        runCurrent(); assertNotNull(sync.state.value.conflict)
        sync.cancelChoice(); advanceTimeBy(2001); runCurrent()
        assertNull(sync.state.value.conflict); assertNull(sync.state.value.error)
        assertFalse(sync.state.value.ready)
        assertTrue(StudyProtocol.sameStudy(before,store.local))
        assertEquals(0,store.imports); assertEquals(0,api.writes)
        sync.pull(); runCurrent()
        assertNotNull(sync.state.value.conflict); assertEquals(0,api.writes)
    }
    @Test fun failedCloudChoiceRetainsLocalDataAndDialogForRetry() = scenario {
        val store = Store(); val expected = copy(store.local); val api = Api(); val sync = coordinator(store,api)
        runCurrent(); api.error = IOException("fixture network failure")
        sync.resolve(1); runCurrent()
        assertNotNull(sync.state.value.conflict); assertEquals("fixture network failure",sync.state.value.error)
        assertFalse(sync.state.value.busy); assertFalse(sync.state.value.ready)
        assertTrue(StudyProtocol.sameStudy(expected,store.local)); assertNull(store.applied)
        api.error = null; sync.resolve(1); runCurrent()
        assertTrue(sync.state.value.ready); assertNull(sync.state.value.conflict)
        assertTrue(StudyProtocol.sameStudy(api.remote,store.local))
    }
    @Test fun logoutWhileCloudChoiceCompletesCannotAdoptIntoGuest() = scenario {
        val store = Store(); val api = Api(); val account = Account(); val sync = coordinator(store,api,account)
        runCurrent(); api.onRead = { sync.logout() }
        sync.resolve(1); runCurrent()
        assertEquals("",store.studyOwner); assertEquals(0,store.imports)
        assertFalse(sync.state.value.ready); assertNull(store.applied)
    }
    @Test fun expiredChoiceDoesNotKeepPromptForLoggedOutAccount() = scenario {
        val store = Store(); val api = Api(); val account = Account(); val sync = coordinator(store,api,account)
        runCurrent(); api.error = StudyApiException(401)
        sync.resolve(1); runCurrent()
        assertTrue(account.state.value.expired); assertFalse(sync.state.value.ready)
        assertNull(sync.state.value.conflict); assertEquals(0,store.imports)
    }
    @Test fun rapidRepeatedCloudClicksStartOnlyOneRefresh() = scenario {
        val store = Store(); val api = Api(); val sync = coordinator(store,api)
        runCurrent(); repeat(4) { sync.resolve(1) }; runCurrent()
        assertEquals(2,api.reads); assertEquals(1,store.imports)
    }
    @Test fun localUploadChoiceIncludesEditsMadeWhileDialogWasOpen() = scenario {
        val store = Store(); val api = Api(); val sync = coordinator(store,api)
        runCurrent(); store.local = study(99,"recent edit")
        sync.resolve(2); runCurrent()
        assertEquals("recent edit",api.remote.data.getJSONArray("todos").getJSONObject(0).getString("text"))
        assertEquals(1,api.writes); assertTrue(sync.state.value.ready)
    }
    @Test fun failedLocalUploadIsNotDiscardedByRestartBaselineDetection() = scenario {
        val store = Store(); val api = Api(); val first = coordinator(store,api)
        runCurrent(); api.error = IOException("fixture upload failure")
        first.resolve(2); runCurrent()
        assertTrue(store.pendingStudyUpload); first.close()
        val local = copy(store.local); api.error = null
        val restarted = coordinator(store,api); runCurrent()
        assertTrue(StudyProtocol.sameStudy(local,store.local))
        assertNotNull(restarted.state.value.conflict)
        restarted.resolve(1); runCurrent(); assertFalse(store.pendingStudyUpload)
    }
    @Test fun equalWebAndNativeDataWithDefaultFieldsAndFloatVolumeDoesNotAsk() = scenario {
        val remote = study(935*60).also {
            it.data.getJSONObject("stats").put("totalPomodoros",32)
            it.data.getJSONObject("settings").put("notificationVolume",0.6)
        }
        val store = Store(remote)
        store.local.data.getJSONObject("stats").put("lastDate","Fri Oct 09 2026")
            .put("todayStudyTime",0).put("todayPomodoros",0)
        store.local.data.getJSONArray("todos").getJSONObject(0).put("id","123").put("pinned",false)
        store.local.data.getJSONObject("settings").put("notificationVolume",0.6f.toDouble())
            .put("breakDuration",5).put("countdownDuration",10).put("pomodoroCount",4)
            .put("timerMode","pomodoro").put("pauseMusicDuringBreak",true)
            .put("showHitokoto",true).put("hidePomodoroOnIdle",false).put("countdowns",org.json.JSONArray())
        store.local.calendar.getJSONObject("dailyLog").put("2026-10-09",JSONObject("""{"studyTime":0,"pomodoros":0}"""))
        val api = Api(remote); val sync = coordinator(store,api); runCurrent()
        assertNull(sync.state.value.conflict); assertTrue(sync.state.value.ready)
        assertEquals(0,api.writes)
        sync.close()
        val restarted = coordinator(store,api); runCurrent()
        assertNull(restarted.state.value.conflict); assertTrue(restarted.state.value.ready)
    }
    @Test fun cloudMidnightRolloverDoesNotConflictWithOfflineTodoEdit() = scenario {
        val api = Api(); val store = Store(api.remote)
        store.cloud = copy(api.remote); store.applied = copy(store.local)
        store.local.data.getJSONArray("todos").getJSONObject(0).put("text","offline edit")
        val expected = copy(store.local)
        api.remote.data.getJSONObject("stats").put("lastDate","Fri Oct 09 2026")
            .put("todayStudyTime",0).put("todayPomodoros",0)
        api.remote.calendar.getJSONObject("dailyLog").put("2026-10-09",JSONObject("""{"studyTime":0,"pomodoros":0}"""))
        val sync = coordinator(store,api); runCurrent()
        assertNull(sync.state.value.conflict); assertTrue(sync.state.value.ready)
        assertTrue(StudyProtocol.sameStudy(expected,store.local)); assertEquals(0,api.writes)
    }
    @Test fun identicalGuestAndCloudDoNotNeedAccountChoice() = scenario {
        val api = Api(); val store = Store(api.remote).also { it.studyOwner = "" }
        val sync = coordinator(store,api); runCurrent()
        assertNull(sync.state.value.conflict); assertTrue(sync.state.value.ready)
        assertEquals("u",store.studyOwner); assertEquals(0,api.writes)
    }

    @Test fun sparseMergeUploadFailureKeepsFiniteLocalHistoryAndCanRetry() = scenario {
        val store = Store()
        val remote = study(120,"remote")
        val day = remote.calendar.getJSONObject("dailyLog").remove("2026-10-07")
        remote.calendar.getJSONObject("dailyLog").put("2026-10-08",day)
        val api = Api(remote); val sync = coordinator(store,api)
        runCurrent(); assertNotNull(sync.state.value.conflict)
        api.error = StudyApiException(500,"上传日历")
        sync.resolve(0); runCurrent()
        assertTrue(store.pendingStudyUpload)
        assertTrue(sync.state.value.error!!.contains("上传日历"))
        assertEquals(30.0,store.local.calendar.getJSONObject("dailyLog").getJSONObject("2026-10-07").getDouble("studyTime"),0.0)
        assertEquals(120.0,store.local.calendar.getJSONObject("dailyLog").getJSONObject("2026-10-08").getDouble("studyTime"),0.0)
        assertEquals("local",store.local.data.getJSONArray("todos").getJSONObject(0).getString("text"))
        api.error = null; sync.syncNow(); runCurrent()
        assertFalse(store.pendingStudyUpload); assertNull(sync.state.value.error)
        assertEquals(2,api.writes)
    }
}
