package com.shshouse.studywithmiku

import kotlinx.coroutines.flow.StateFlow

/** Small boundaries let sync lifecycle and account changes be tested without a live account. */
interface StudySyncStore {
    val revision: StateFlow<Long>
    val studyOwner: String
    val autoSync: Boolean
    val pendingStudyUpload: Boolean
    fun studySnapshot(): CloudStudy
    fun lastCloudStudy(): CloudStudy?
    fun lastAppliedStudy(): CloudStudy?
    fun rememberAppliedStudy(study: CloudStudy,pendingUpload: Boolean = false)
    fun switchStudyOwner(id: String)
    fun applyStudy(study: CloudStudy)
    fun rememberCloud(study: CloudStudy)
}
interface StudySyncTimer {
    val state: StateFlow<FocusClock>
    fun pause()
    fun flush()
    fun restoreFromStore()
}
interface StudySyncAccount {
    val state: StateFlow<AccountState>
    fun token(): String
    fun expire()
    fun logout()
}
interface StudySyncApi {
    fun fetch(token: String): CloudStudy
    fun push(token: String,study: CloudStudy)
}
