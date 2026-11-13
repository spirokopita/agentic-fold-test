package com.agentfold.app

import com.agentfold.app.agent.ActivityStage
import com.agentfold.app.ui.neural.VisualState
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveVisualSignalsTest {
    @Test fun publicRuntimeStagesMapWithoutInventingPeerSignals() {
        for (stage in ActivityStage.entries) {
            val signal = liveVisualSignal(stage, VoiceVisualPhase.OFF)
            assertEquals(if (stage.busy) VisualState.INTERNAL_THINKING else VisualState.THINKING_FIELD, signal.state)
            assertEquals(if (stage.busy) .8f else 0f, signal.activity)
        }
    }

    @Test fun voicePhasesOnlyAnimateLocalThoughtActivity() {
        assertEquals(LiveVisualSignal(VisualState.THINKING_FIELD, .35f),
            liveVisualSignal(ActivityStage.IDLE, VoiceVisualPhase.LISTENING))
        assertEquals(LiveVisualSignal(VisualState.INTERNAL_THINKING, .65f),
            liveVisualSignal(ActivityStage.IDLE, VoiceVisualPhase.PROCESSING))
        assertEquals(LiveVisualSignal(VisualState.THINKING_FIELD, .2f),
            liveVisualSignal(ActivityStage.IDLE, VoiceVisualPhase.SPEAKING))
    }

    @Test fun runtimeWorkTakesPrecedenceOverVoicePhase() {
        assertEquals(LiveVisualSignal(VisualState.INTERNAL_THINKING, .8f),
            liveVisualSignal(ActivityStage.TOOL, VoiceVisualPhase.LISTENING))
    }
}
