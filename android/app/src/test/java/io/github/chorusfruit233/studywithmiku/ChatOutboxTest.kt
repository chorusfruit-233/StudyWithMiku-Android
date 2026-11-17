package io.github.chorusfruit233.studywithmiku

import org.junit.Assert.*
import org.junit.Test

class ChatOutboxTest {
    private class Fixture {
        var time = 1_000L
        var disk = "[]"
        var writable = true
        val sent = mutableListOf<ChatDraft>()
        fun box() = ChatOutbox({ disk },{ if (writable) { disk = it; true } else false },{ time })
        val outbox = box()
        fun send(reliable: Boolean = true,user: String = "u",ready: Boolean = true) = outbox.pump(user,ready,reliable) { sent.add(it); true }
        fun enqueue(text: String = "draft") = outbox.enqueue("u","Miku",text,"")
    }
    @Test fun disconnectedQueueSurvivesRestartAndDoesNotSendForAnotherAccount() {
        val f = Fixture(); assertTrue(f.enqueue()); f.send(ready = false); assertTrue(f.sent.isEmpty())
        val restored = f.box()
        restored.pump("other",true,true) { fail("Wrong account"); true }
        restored.pump("u",true,true) { f.sent.add(it); true }
        assertEquals("draft",f.sent.single().content); assertEquals(f.outbox.pending("u").single().clientId,f.sent.single().clientId)
    }
    @Test fun ackTimeoutRetriesSameIdAtMostThreeTimesThenPreservesFailedDraft() {
        val f = Fixture(); f.enqueue("[reply:original][sticker:21]")
        repeat(4) { f.send(); f.time += 10_001 }
        assertEquals(3,f.sent.size); assertEquals(1,f.sent.map { it.clientId }.distinct().size)
        assertTrue(f.outbox.pending("u").isEmpty())
        assertEquals("[reply:original][sticker:21]",f.box().failed("u").single().content)
    }
    @Test fun unrelatedAckCannotReleaseTheInflightMessageOrSendTheNextOne() {
        val f = Fixture(); f.enqueue("first"); f.enqueue("second"); f.send()
        f.time += 2000
        assertNull(f.outbox.confirm("u","unknown")); f.send(); assertEquals(1,f.sent.size)
        assertNull(f.outbox.confirm("other",f.sent.first().clientId))
        f.outbox.confirm("u",f.sent.first().clientId); f.send()
        assertEquals(listOf("first","second"),f.sent.map { it.content })
    }
    @Test fun legacyServerGetsOnlyOneAttemptAndCapabilityDowngradeCannotDuplicateUnconfirmedMessage() {
        val f = Fixture(); f.enqueue(); f.send(reliable = false); f.time += 20_000; f.send(reliable = false)
        assertEquals(1,f.sent.size); assertTrue(f.outbox.pending("u").isEmpty())
        f.enqueue("unconfirmed"); f.send(); f.outbox.disconnect(); f.time += 2000; f.send(reliable = false)
        assertEquals(2,f.sent.size); assertEquals("unconfirmed",f.outbox.failed("u").single().content)
    }
    @Test fun storageFailureDoesNotClearInputOrSendAnything() {
        val f = Fixture(); f.writable = false
        assertFalse(f.enqueue()); f.send(); assertTrue(f.sent.isEmpty()); assertNotNull(f.outbox.error)
        f.writable = true; f.enqueue(); f.writable = false; f.send(); assertTrue(f.sent.isEmpty())
    }
    @Test fun serverRejectionBackoffIsBoundedAndUnauthorizedBecomesDraft() {
        val f = Fixture(); f.enqueue(); f.send(); val id = f.sent.single().clientId
        f.outbox.rejected("u",id,"persistence_failed"); f.time += 1999; f.send(); assertEquals(1,f.sent.size)
        f.time++; f.send(); assertEquals(2,f.sent.size)
        f.outbox.rejected("u",id,"unauthorized"); f.time += 100_000; f.send()
        assertEquals(2,f.sent.size); assertEquals(id,f.outbox.failed("u").single().clientId)
    }
    @Test fun expiryPreservesContentWithoutAutomaticallySendingOldMessages() {
        val f = Fixture(); f.enqueue(); f.time += 24*60*60_000L+1; f.send()
        assertTrue(f.sent.isEmpty()); assertTrue(f.outbox.failed("u").single().failure.contains("24"))
    }
    @Test fun socketRefusalDoesNotConsumeAttemptsOrLoseQueuedMessage() {
        val f = Fixture(); f.enqueue()
        repeat(5) { f.outbox.pump("u",true,true) { false }; f.time += 2000 }
        assertEquals(0,f.outbox.pending("u").single().attempts)
        f.send(); assertEquals(1,f.sent.single().attempts)
    }
}
