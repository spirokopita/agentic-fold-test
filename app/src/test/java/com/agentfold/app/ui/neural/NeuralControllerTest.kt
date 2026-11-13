package com.agentfold.app.ui.neural

import org.junit.Assert.*
import org.junit.Test

class NeuralControllerTest {
    @Test fun unavailableEndpointsCannotAppearConnected() {
        val controller = NeuralController()
        controller.setConnectedApplications(listOf(ApplicationEndpoint("a", "app", false, true)))
        assertFalse(controller.snapshot.applications.single().connected)
    }
    @Test fun simulationIsExplicitAndResetRemovesAllSimulatedData() {
        val controller = NeuralController()
        controller.preview(VisualState.LOCAL_INFECTED)
        assertTrue(controller.snapshot.preview)
        assertTrue(controller.snapshot.localFirewall.simulated)
        assertTrue(controller.snapshot.localInfection > 0)
        controller.endPreview()
        assertEquals(NeuralSnapshot(), controller.snapshot)
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
