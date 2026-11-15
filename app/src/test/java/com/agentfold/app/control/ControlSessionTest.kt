package com.agentfold.app.control

import org.junit.Assert.*
import org.junit.Test

class ControlSessionTest {
    private var clock = 1000L
    private val session = ControlSession { clock }
    private val frame = WindowFrame(9, "test.app", 500, 40, 1000, 900)

    @Test fun noActionsWithoutManualSession() { assertNull(session.prepare(frame)) }
    @Test fun stopInvalidatesConfirmedAndQueuedActions() {
        session.start(); val ticket = session.prepare(frame)!!
        session.stop()
        assertFalse(session.consume(ticket, frame)); assertFalse(session.active)
    }
    @Test fun staleCallbackCannotAffectNewSession() {
        session.start(); val old = session.prepare(frame)!!; session.consume(old, frame)
        session.stop(); session.start(); val current = session.prepare(frame)!!
        assertFalse(session.finish(old)); assertTrue(session.busy)
        assertTrue(session.consume(current, frame))
    }
    @Test fun authorizationIsSingleUseAndSingleFlight() {
        session.start(); val ticket = session.prepare(frame)!!
        assertNull(session.prepare(frame)); assertTrue(session.consume(ticket, frame))
        assertFalse(session.consume(ticket, frame)); assertNull(session.prepare(frame))
        session.finish(ticket); assertNotNull(session.prepare(frame))
    }
    @Test fun approvalAndSessionExpire() {
        session.start(); val ticket = session.prepare(frame)!!
        clock += ControlSession.APPROVAL_MS
        assertFalse(session.consume(ticket, frame))
        clock += ControlSession.SESSION_MS
        assertFalse(session.active); assertNull(session.prepare(frame))
    }
    @Test fun changedPackageWindowBoundsOrDisplayRejectsAction() {
        for (different in listOf(frame.copy(packageName="attacker.app"), frame.copy(id=10),
            frame.copy(right=1200), frame.copy(top=80), frame.copy(displayId=1))) {
            session.start(); val ticket = session.prepare(frame)!!
            assertFalse(session.consume(ticket, different))
        }
    }
    @Test fun percentagesUseActualApplicationBounds() {
        assertEquals(750f, frame.point(50, 50)!!.first, .001f)
        assertEquals(470f, frame.point(50, 50)!!.second, .001f)
        assertNull(frame.point(0, 50)); assertNull(frame.point(50, 100))
    }
    @Test fun restoredProcessHasNoAuthority() { assertFalse(ControlSession { clock }.active) }
    @Test fun externalDisplaysAndInvalidBoundsRejected() {
        session.start(); assertNull(session.prepare(frame.copy(displayId=2)))
        assertNull(session.prepare(frame.copy(right=400)))
    }
    @Test fun verificationNeverTreatsDispatchAsSuccess() {
        assertEquals(Verification.UNCHANGED, ActionVerification.compare("a", "a", true))
        assertEquals(Verification.CHANGED, ActionVerification.compare("a", "b", true))
        assertEquals(Verification.TARGET_LOST, ActionVerification.compare("a", null, true))
        assertEquals(Verification.TARGET_LOST, ActionVerification.compare("a", "b", false))
    }
}
