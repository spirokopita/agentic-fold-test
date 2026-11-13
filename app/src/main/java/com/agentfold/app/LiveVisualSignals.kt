package com.agentfold.app

import com.agentfold.app.agent.ActivityStage
import com.agentfold.app.ui.neural.VisualState

/**
 * Maps public application lifecycle signals into visual-only renderer inputs.
 * It never dispatches work and deliberately has no peer, firewall, infection or endpoint mapping.
 */
data class LiveVisualSignal(val state: VisualState, val activity: Float)

fun liveVisualSignal(stage: ActivityStage, voicePhase: VoiceVisualPhase): LiveVisualSignal = when {
    stage.busy -> LiveVisualSignal(VisualState.INTERNAL_THINKING, .8f)
    voicePhase == VoiceVisualPhase.PROCESSING -> LiveVisualSignal(VisualState.INTERNAL_THINKING, .65f)
    voicePhase == VoiceVisualPhase.LISTENING -> LiveVisualSignal(VisualState.THINKING_FIELD, .35f)
    voicePhase == VoiceVisualPhase.SPEAKING -> LiveVisualSignal(VisualState.THINKING_FIELD, .2f)
    else -> LiveVisualSignal(VisualState.THINKING_FIELD, 0f)
}
