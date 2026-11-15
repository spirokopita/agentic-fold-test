package com.agentfold.app.ui.neural

import org.junit.Assert.*
import org.junit.Test

class NeuralControllerTest {
    @Test fun unavailableEndpointsCannotAppearConnected() {
        val controller = NeuralController()
        controller.setConnectedApplications(listOf(
            ApplicationEndpoint("a", "absent", false, true),
            ApplicationEndpoint("b", "not launchable", true, true, launchable = false),
        ))
        assertTrue(controller.snapshot.applications.none { it.connected })
    }
    @Test fun simulationIsExplicitAndResetRemovesAllSimulatedData() {
        val controller = NeuralController()
        controller.preview(VisualState.LOCAL_INFECTED)
        assertTrue(controller.snapshot.preview)
        assertTrue(controller.snapshot.localFirewall.simulated)
        assertTrue(controller.snapshot.localInfection > 0)
        controller.preview(VisualState.EXTERNAL_EXPLORATION)
        assertEquals(6, controller.snapshot.applications.size)
        assertTrue(controller.snapshot.applications.all { it.label.startsWith("sim ") })
        assertTrue(controller.snapshot.applications.any { it.connected })
        assertTrue(controller.snapshot.applications.any { it.available && !it.launchable })
        controller.endPreview()
        assertEquals(NeuralSnapshot(), controller.snapshot)
    }
    @Test fun independentControlApiPreservesTruthBoundaries() {
        val controller = NeuralController()
        controller.setVisualState(VisualState.CONFLICT)
        controller.setThoughtActivity(.4f)
        controller.setPeerAIConnection(PeerConnection.CONTESTED)
        controller.setConnectionColourBalance(.2f)
        controller.setFirewallMetrics(
            FirewallMetric("owner-defined local", 84f),
            FirewallMetric("owner-defined peer", null),
        )
        controller.setInfectionVisualState(OrbTarget.LOCAL, .3f)
        controller.setInfectionVisualState(OrbTarget.PEER, .6f)
        val snapshot = controller.snapshot
        assertEquals(VisualState.CONFLICT, snapshot.state)
        assertEquals(.4f, snapshot.activity)
        assertEquals(PeerConnection.CONTESTED, snapshot.peer)
        assertEquals(.2f, snapshot.balance)
        assertEquals(84f, snapshot.localFirewall.percent)
        assertNull(snapshot.peerFirewall.percent)
        assertFalse(snapshot.localFirewall.simulated)
        assertEquals(.3f, snapshot.localInfection)
        assertEquals(.6f, snapshot.peerInfection)
        assertFalse(snapshot.preview)
    }

    @Test fun invalidSignalsCannotReachRenderer() {
        val controller = NeuralController()
        controller.setThoughtActivity(Float.NaN)
        controller.setConnectionColourBalance(4f)
        controller.setFirewallMetrics(FirewallMetric(percent = Float.POSITIVE_INFINITY), FirewallMetric(percent = -2f))
        assertEquals(0f, controller.snapshot.activity)
        assertEquals(1f, controller.snapshot.balance)
        assertNull(controller.snapshot.localFirewall.percent)
        assertEquals(0f, controller.snapshot.peerFirewall.percent!!)
    }
}
