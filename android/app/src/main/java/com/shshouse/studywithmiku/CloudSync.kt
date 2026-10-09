package com.shshouse.studywithmiku

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.time.Instant

data class SyncConflict(val local: CloudStudy,val remote: CloudStudy,val switching: Boolean)
data class SyncState(val busy: Boolean = false,val ready: Boolean = false,val lastSync: Long = 0,
    val error: String? = null,val conflict: SyncConflict? = null,val message: String? = null)
class CloudSync(private val store: StudySyncStore,private val timer: StudySyncTimer,private val auth: StudySyncAccount,
    private val api: StudySyncApi,private val networkDispatcher: CoroutineDispatcher = Dispatchers.IO) {
    private val scope = CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(SyncState())
    val state = _state.asStateFlow()
    private var task: Job? = null
    private var lastRevision = -1L
    private var activeUser = ""
    private var started = false
    @OptIn(FlowPreview::class)
    fun start() {
        if (started) return
        started = true
        scope.launch {
            auth.state.map { it.userId.takeIf { _ -> it.loggedIn } ?: "" }.distinctUntilChanged().collect { user ->
                task?.cancel(); activeUser = user; lastRevision = -1L; _state.value = SyncState()
                if (user.isNotEmpty()) pull()
            }
        }
        scope.launch {
            store.revision.debounce(2000).collect { if (store.autoSync && _state.value.ready && _state.value.conflict == null && it != lastRevision && !timer.state.value.running) syncNow() }
        }
        scope.launch {
            while (isActive) { delay(60_000); if (store.autoSync && _state.value.ready && _state.value.conflict == null && store.revision.value != lastRevision) syncNow() }
        }
    }
    private fun hasData(study: CloudStudy): Boolean = (study.data.optJSONObject("stats")?.optDouble("totalStudyTime") ?: 0.0) > 0 ||
        (study.calendar.optJSONObject("dailyLog")?.length() ?: 0) > 0 || (study.data.optJSONObject("stats")?.optInt("totalPomodoros") ?: 0) > 0 ||
        (study.data.optJSONArray("todos")?.length() ?: 0) > 0 || (study.calendar.optJSONObject("plans")?.length() ?: 0) > 0 ||
        (study.data.optJSONObject("settings")?.optJSONArray("countdowns")?.length() ?: 0) > 0
    private fun current(user: String) = activeUser == user && auth.state.value.loggedIn && auth.state.value.userId == user
    private fun launchTask(block: suspend CoroutineScope.() -> Unit) {
        // Assign before starting: immediate nested follow-up uploads must remain cancellable.
        val next = scope.launch(start = CoroutineStart.LAZY,block = block)
        task = next
        next.start()
    }
    fun pull() {
        if (activeUser.isBlank() || _state.value.busy) return
        val user = activeUser
        _state.value = _state.value.copy(busy = true,error = null,message = null)
        launchTask {
            try {
                if (store.studyOwner.isNotBlank() && store.studyOwner != user) {
                    timer.pause(); store.switchStudyOwner(user); timer.restoreFromStore()
                }
                val token = auth.token(); check(token.isNotBlank()) { "请先登录" }
                val remote = withContext(networkDispatcher) { api.fetch(token) }
                ensureActive()
                if (!current(user)) return@launchTask
                timer.flush()
                val local = store.studySnapshot()
                val switching = store.studyOwner != user
                val remoteExists = remote.data.length() > 0 || remote.calendar.length() > 0
                val previousRemote = if (!switching) store.lastCloudStudy() else null
                val previousLocal = if (!switching) store.lastAppliedStudy() else null
                when {
                    !switching && previousRemote != null && StudyComparison.same(remote,previousRemote) -> {
                        // Includes existing 1.2.1 profiles: accepting cloud data must not prompt again.
                        val unchanged = !store.pendingStudyUpload && previousLocal != null && StudyProtocol.sameLocalStudy(local,previousLocal)
                        if (unchanged) lastRevision = store.revision.value
                        _state.value = SyncState(ready = true,lastSync = Instant.now().toEpochMilli(),
                            message = if (unchanged) "本地与云端资料已一致" else "云端资料未变化，本地改动已保留")
                    }
                    !switching && !store.pendingStudyUpload && previousLocal != null && StudyProtocol.sameLocalStudy(local,previousLocal) -> adopt(remote)
                    StudyComparison.same(local,remote) -> adopt(remote)
                    hasData(local) && (remoteExists || switching) -> _state.value = SyncState(conflict = SyncConflict(local,remote,switching))
                    else -> {
                        val selected = if (remoteExists) remote else local
                        adopt(selected,needsUpload = !remoteExists && hasData(local))
                        if (!remoteExists && hasData(local) && store.autoSync) syncNow()
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (current(user)) failure(e) }
        }
    }
    fun resolve(choice: Int) {
        val conflict = _state.value.conflict ?: return
        if (_state.value.busy || activeUser.isBlank()) return
        val user = activeUser
        _state.value = _state.value.copy(busy = true,error = null,message = null)
        launchTask {
            try {
                val remote = if (choice == 1) {
                    val token = auth.token(); check(token.isNotBlank()) { "请重新登录" }
                    withContext(networkDispatcher) { api.fetch(token) }
                } else conflict.remote
                ensureActive()
                if (!current(user)) return@launchTask
                timer.flush()
                val local = store.studySnapshot()
                val selected = when (choice) { 1 -> remote; 2 -> local; else -> StudyProtocol.merged(local,remote) }
                adopt(selected,needsUpload = choice != 1)
                if (choice != 1) syncNow()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (current(user)) failure(e) }
        }
    }
    private fun adopt(selected: CloudStudy,needsUpload: Boolean = false) {
        _state.value = _state.value.copy(busy = true,error = null)
        timer.pause()
        store.switchStudyOwner(activeUser)
        store.applyStudy(selected)
        timer.restoreFromStore()
        // Record the native projection as well as the original cloud JSON; both survive restarts.
        store.rememberAppliedStudy(store.studySnapshot(),pendingUpload = needsUpload)
        // Keep the chosen snapshot local; only explicit/automatic sync writes to the server.
        lastRevision = store.revision.value
        _state.value = SyncState(ready = true,lastSync = Instant.now().toEpochMilli(),message = "学习资料已保存到本机")
    }
    fun syncNow() {
        if (_state.value.busy || _state.value.conflict != null || activeUser.isBlank()) return
        if (!_state.value.ready) { pull(); return }
        val user = activeUser
        _state.value = _state.value.copy(busy = true,error = null,message = null)
        launchTask {
            try {
                timer.flush()
                val snapshot = store.studySnapshot()
                val revision = store.revision.value
                val token = auth.token(); check(token.isNotBlank()) { "请重新登录" }
                withContext(networkDispatcher) { api.push(token,snapshot) }
                ensureActive()
                // A completed old request must never update the next account or guest profile.
                if (!current(user) || store.studyOwner != user) return@launchTask
                store.rememberCloud(snapshot)
                store.rememberAppliedStudy(snapshot)
                lastRevision = revision
                _state.value = SyncState(ready = true,lastSync = Instant.now().toEpochMilli(),message = "学习资料已上传云端")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (current(user)) failure(e) }
        }
    }
    private fun failure(e: Exception) {
        if (e is StudyApiException && e.status == 401) auth.expire()
        _state.value = _state.value.copy(busy = false,error = e.message ?: "同步失败，请检查网络",message = null)
    }
    fun logout() {
        task?.cancel(); timer.pause(); activeUser = ""; _state.value = SyncState()
        store.switchStudyOwner(""); timer.restoreFromStore(); auth.logout()
    }
    fun cancelChoice() { if (!_state.value.busy) _state.value = _state.value.copy(conflict = null,error = "请选择本地与云端资料的同步方式后再同步") }
    internal fun close() { scope.cancel() }
}
