package com.agentfold.app.agent

/** Public execution telemetry only. Never model reasoning or inferred task progress. */
enum class ActivityStage(val label: String, val busy: Boolean = false) {
    IDLE("READY"), PLANNING("PLAN", true), MODEL("MODEL", true), TOOL("TOOL", true),
    VERIFYING("VERIFY", true), COMPLETED("DONE"), CANCELLED("STOPPED"), FAILED("ERROR")
}

enum class ToolOutcome { NONE, ACCEPTED, REJECTED }

data class TaskActivity(
    val stage: ActivityStage = ActivityStage.IDLE,
    val objective: String = "No task running",
    val modelLabel: String? = null,
    val tool: String? = null,
    val modelCall: Int = 0,
    val fileApproved: Boolean = false,
    val verifiedReport: Boolean = false,
    val toolOutcome: ToolOutcome = ToolOutcome.NONE
)
