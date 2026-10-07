package com.shshouse.studywithmiku

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.time.Instant

data class SyncConflict(val local: CloudStudy,val remote: CloudStudy,val switching: Boolean)
data class SyncState(val busy: Boolean = false,val ready: Boolean = false,val lastSync: Long = 0,
    val error: String? = null,val conflict: SyncConflict? = null)
class CloudSync(private val store: MikuStore,private val timer: FocusEngine,private val auth: StudyAuth,private val api: StudyApi) {
    private val scope = CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(SyncState())
    val state = _state.asStateFlow()
    private var task: Job? = null
    private var lastRevision = -1L
    private var activeUser = ""
    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch {
            auth.state.map { it.userId.takeIf { _ -> it.loggedIn } ?: "" }.distinctUntilChanged().collect { user ->
                task?.cancel(); activeUser = user; _state.value = SyncState()
                if (user.isNotEmpty()) pull()
            }
        }
        scope.launch {
            store.revision.debounce(2000).collect { if (store.autoSync && _state.value.ready && it != lastRevision && !timer.state.value.running) syncNow() }
        }
        scope.launch {
            while (isActive) { delay(60_000); if (store.autoSync && _state.value.ready && store.revision.value != lastRevision) syncNow() }
        }
    }
    private fun hasData(study: CloudStudy): Boolean = (study.data.optJSONObject("stats")?.optDouble("totalStudyTime") ?: 0.0) > 0 ||
        (study.calendar.optJSONObject("dailyLog")?.length() ?: 0) > 0 || (study.data.optJSONObject("stats")?.optInt("totalPomodoros") ?: 0) > 0 ||
        (study.data.optJSONArray("todos")?.length() ?: 0) > 0 || (study.calendar.optJSONObject("plans")?.length() ?: 0) > 0 ||
        (study.data.optJSONObject("settings")?.optJSONArray("countdowns")?.length() ?: 0) > 0
    fun pull() {
        if (activeUser.isBlank() || _state.value.busy) return
        task = scope.launch {
            _state.value = _state.value.copy(busy = true,error = null)
            try {
                if (store.studyOwner.isNotBlank() && store.studyOwner != activeUser) {
                    timer.pause(); store.switchStudyOwner(activeUser); timer.restoreFromStore()
                }
                val token = auth.token(); check(token.isNotBlank()) { "请先登录" }
                val remote = withContext(Dispatchers.IO) { api.fetch(token) }
                ensureActive()
                timer.flush()
                val local = store.studySnapshot()
                val switching = store.studyOwner != activeUser
                val remoteExists = remote.data.length() > 0 || remote.calendar.length() > 0
                if (hasData(local) && (remoteExists || switching)) {
                    _state.value = SyncState(conflict = SyncConflict(local,remote,switching))
                } else {
                    val selected = if (remoteExists) remote else local
                    adopt(selected)
                    if (!remoteExists && hasData(local) && store.autoSync) syncNow()
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { failure(e) }
        }
    }
    fun resolve(choice: Int) {
        val conflict = _state.value.conflict ?: return
        val selected = when (choice) { 1 -> conflict.remote; 2 -> conflict.local; else -> StudyProtocol.merged(conflict.local,conflict.remote) }
        task = scope.launch {
            try { adopt(selected); if (choice != 1) syncNow() } catch (e: CancellationException) { throw e } catch (e: Exception) { failure(e) }
        }
    }
    private suspend fun adopt(selected: CloudStudy) {
        _state.value = _state.value.copy(busy = true,conflict = null,error = null)
        timer.pause()
        store.switchStudyOwner(activeUser)
        store.applyStudy(selected)
        timer.restoreFromStore()
        // Keep the chosen snapshot local; only explicit/automatic sync writes to the server.
        lastRevision = store.revision.value
        _state.value = SyncState(ready = true,lastSync = Instant.now().toEpochMilli())
    }
    fun syncNow() {
        if (!_state.value.ready) { pull(); return }
        if (_state.value.busy || activeUser.isBlank()) return
        task = scope.launch {
            _state.value = _state.value.copy(busy = true,error = null)
            try {
                timer.flush()
                val snapshot = store.studySnapshot()
                val revision = store.revision.value
                val token = auth.token(); check(token.isNotBlank()) { "请重新登录" }
                withContext(Dispatchers.IO) { api.push(token,snapshot) }
                ensureActive()
                store.rememberCloud(snapshot)
                lastRevision = revision
                _state.value = SyncState(ready = true,lastSync = Instant.now().toEpochMilli())
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { failure(e) }
        }
    }
    private fun failure(e: Exception) {
        if (e is StudyApiException && e.status == 401) auth.expire()
        _state.value = _state.value.copy(busy = false,error = e.message ?: "同步失败，请检查网络",conflict = null)
    }
    fun logout() {
        task?.cancel(); timer.pause(); activeUser = ""; _state.value = SyncState()
        store.switchStudyOwner(""); timer.restoreFromStore(); auth.logout()
    }
    fun cancelChoice() { _state.value = _state.value.copy(conflict = null,error = "请选择本地与云端资料的同步方式后再同步") }
}
