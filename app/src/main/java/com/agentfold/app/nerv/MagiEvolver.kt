package com.agentfold.app.nerv

// MAGI EVOLVER — Stage 1 (self-save), 25 Nov 2026.
//
// What this file is: the loop that lets MAGI add skills by itself. After
// a CHAT debate finishes, evaluateAfterChat() decides two things:
//   1. Usage: which skills were in MAGI's prompt for a run that worked.
//      Those get useCount + 1 and a fresh lastUsedAt. This is what the
//      auto-skill prune rule reads.
//   2. Auto-save: whether the run is worth keeping as a new skill.
//
// Auto-save is FULLY AUTOMATIC — no approval prompt for the save, exactly
// as Spiros approved. The safety is in the checks, not in a prompt. A
// run is auto-saved only when EVERY one of these is true:
//   - The answer was live (not LOCAL DEMO) and the debate approved it
//     (at least 2 of 3 nodes answered live — the same rule the app shows
//     as "RESOLUTION APPROVED").
//   - The run used at least 2 different real tools — a procedure, not a
//     one-off question — and every real tool call returned ok.
//   - No phone-control tool was used anywhere in the run, and no phone
//     task was proposed. Phone procedures can only be taught by hand.
//   - No secret, key, token, password, phone number, email or long
//     token-like string appears in the question, the tool arguments or
//     the saved instruction. When in doubt the save is refused.
//   - It is not a duplicate of a skill that already exists.
//   - The saved instruction records the STEPS (tool names + the question
//     it answers), never the results, so no reading or private data is
//     ever stored in a skill.
//
// Guardrails that apply here:
//   - Auto skills are capped at 10 (TaughtSkillStore.MAX_AUTO_SKILLS).
//     When full, the oldest auto skill that has never been used is
//     pruned first. A skill MAGI actually uses is never pruned for a new
//     one — if every auto skill has been used, the save is refused.
//   - Auto skills are marked origin AUTO, appear in SETUP > TAUGHT
//     SKILLS with an AUTO tag, and can be unloaded or deleted exactly
//     like a taught skill.
//   - This class never touches keys, the network, permissions or phone
//     control. It only reads the debate result and writes to the skill
//     store.
//   - Runs from routines (unattended) never reach this class — only the
//     CHAT path in NervActivity calls it, where Spiros is present and
//     can see the AUTO tag and delete the skill.

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MagiEvolver(private val skillStore: TaughtSkillStore) {

    companion object {
        // Tools that never execute from a MAGI text answer — the tool call
        // only returns guidance ("use AgentBridge", "needs approval",
        // "tap TALK"). They never count as a successful tool use and never
        // go into an auto-saved skill.
        private val GUIDANCE_ONLY_TOOLS = setOf(
            "phone_observe", "phone_tap", "phone_scroll", "phone_type", "phone_back", "phone_home",
            "read_selected_text", "read_selected_pdf", "write_report", "voice_chat",
            "folder_delete"
        )

        // Tools whose runs are NEVER auto-saved, even when every call
        // returned ok. wifi_password_check takes Spiros's own password as
        // an argument — a run that handled a password must never be
        // turned into a skill, even though the skill itself would only
        // record the steps. When in doubt, do not save.
        private val NEVER_AUTO_SAVE_TOOLS = setOf("wifi_password_check")

        // Phone-control tools. A run that used any of these is NEVER
        // auto-saved, even if every call returned ok. Checked on the full
        // call list, not just the real ones, so there is no way around it.
        private val PHONE_CONTROL_TOOLS = setOf(
            "phone_observe", "phone_tap", "phone_scroll", "phone_type", "phone_back", "phone_home"
        )

        // A procedure is at least two different real tools. One tool call
        // is a question, not a skill worth keeping.
        private const val MIN_TOOLS_FOR_AUTO_SAVE = 2

        // Short fixed hint per real tool, used to write the saved
        // instruction. The instruction records the steps, never results.
        private val TOOL_HINTS = mapOf(
            "calc" to "work it out with calc",
            "time" to "check the time",
            "device" to "check the phone",
            "notes" to "save or read a note",
            "fetch" to "fetch the page",
            "dice" to "roll the dice",
            "calendar" to "check the calendar",
            "contact" to "look up the contact",
            "port_test" to "test the port",
            "port_add" to "save the port",
            "folder_list" to "list the folder",
            "folder_create" to "create the folder",
            "folder_rename" to "rename the file or folder",
            "phyphox_list" to "list the phyphox experiments",
            "phyphox_read" to "take a live sensor reading",
            "phyphox_record" to "record the sensors once",
            "phyphox_scan" to "run the mass scan on the recording",
            "wifi_scan" to "scan the nearby WiFi networks",
            "lan_scan" to "list the devices on the connected network",
            "port_scan" to "scan the common ports on a host on the connected network",
            "router_check" to "check the gateway router",
            "wps_check" to "check whether WPS is enabled"
        )

        // Secret screen. Anything matching here refuses the save — when in
        // doubt, do not save. The phone pattern is checked separately
        // below because dates and short numbers must not match it.
        private val SECRET_PATTERNS = listOf(
            // "api key: ...", "password=...", "bearer ..." and friends.
            Regex("(?i)(api[_-]?key|api[_-]?token|password|passwd|secret|bearer|access[_-]?token|private[_-]?key|auth[_-]?token)\\s*[:=]\\s*\\S+"),
            // Known token prefixes (OpenAI, GitHub, Slack).
            Regex("(?i)\\b(sk-[A-Za-z0-9]{8,}|ghp_[A-Za-z0-9]{8,}|xox[baprs]-[A-Za-z0-9\\-]{8,})\\b"),
            // Email addresses.
            Regex("\\b[\\w.+-]+@[\\w-]+\\.[\\w.]+\\b"),
            // Long token-like runs (40+ chars) — hashes, session tokens.
            Regex("\\b[A-Za-z0-9_\\-]{40,}\\b")
        )

        // Phone-like digit runs. Checked with a digit count afterwards, so
        // dates ("2026-11-25", 8 digits) and short numbers never match.
        // Matches: +614..., 0400 601 779, or 10+ contiguous digits.
        private val PHONE_CANDIDATE =
            Regex("(\\+\\d[\\d\\s\\-()]{7,}\\d|0\\d[\\d\\s\\-()]{7,}\\d|\\b\\d{10,}\\b)")
    }

    // Called once per finished CHAT debate, on the background thread.
    // Returns a short note for Spiros when a skill was auto-saved, or null
    // when nothing was saved. Never shows a prompt and never blocks the
    // chat — a refusal is silent, a save gets one short toast in CHAT.
    fun evaluateAfterChat(question: String, result: MagiEngine.MagiResult): String? {
        // 1. Usage tracking first: a run counts as a success for the
        // skills in MAGI's prompt when the answer was live and the debate
        // approved it. This runs whether or not anything is auto-saved.
        val runSucceeded = !result.localDemo && approvedVotes(result) >= 2
        if (runSucceeded) {
            skillStore.markUsed(skillStore.enabled().map { it.id })
        }

        // 2. Auto-save. Every check below must pass.
        if (result.localDemo) return null
        if (approvedVotes(result) < 2) return null
        if (result.phoneTask != null) return null
        if (result.toolCalls.any { it.name in PHONE_CONTROL_TOOLS }) return null
        // EVOLVER: a run that handled Spiros's WiFi password is never
        // auto-saved, even when every call returned ok.
        if (result.toolCalls.any { it.name in NEVER_AUTO_SAVE_TOOLS }) return null
        val realCalls = result.toolCalls.filter { it.name !in GUIDANCE_ONLY_TOOLS }
        if (realCalls.isEmpty()) return null
        if (realCalls.any { !it.ok }) return null
        val tools = realCalls.map { it.name }.distinct()
        if (tools.size < MIN_TOOLS_FOR_AUTO_SAVE) return null
        if (tools.size > TaughtSkillStore.MAX_TOOLS_PER_SKILL) return null
        if (tools.any { it !in TOOL_HINTS }) return null
        val name = TaughtSkillStore.suggestedName(question)
        val instruction = buildInstruction(question, tools)
        if (instruction.isBlank()) return null
        if (containsSecret(question) || containsSecret(instruction)) return null
        if (realCalls.any { containsSecret(it.args) }) return null
        if (isDuplicate(name, instruction, tools)) return null
        val error = skillStore.addAuto(name, instruction, tools)
        if (error != null) return null
        return "MAGI saved a new skill: $name [AUTO] — see SETUP > TAUGHT SKILLS to unload or delete it."
    }

    // Same rule the app shows as "RESOLUTION APPROVED": at least 2 of 3
    // nodes answered live.
    private fun approvedVotes(result: MagiEngine.MagiResult): Int =
        result.votes.count { it.second }

    // The saved instruction: the question it answers plus the steps that
    // worked. Never the results — no reading, number or private text from
    // the run is ever stored in a skill.
    private fun buildInstruction(question: String, tools: List<String>): String {
        val shortQuestion = question.trim().replace(Regex("\\s+"), " ").take(120)
        val steps = tools.mapNotNull { TOOL_HINTS[it] }.joinToString(", then ")
        if (shortQuestion.isEmpty() || steps.isEmpty()) return ""
        val text = "When Spiros asks \"$shortQuestion\": $steps. " +
            "(Auto-saved — learned from a run where every step worked.)"
        return text.take(TaughtSkillStore.MAX_INSTRUCTION_CHARS)
    }

    private fun isDuplicate(name: String, instruction: String, tools: List<String>): Boolean {
        return skillStore.all().any { existing ->
            existing.instruction == instruction ||
                (existing.name.equals(name, ignoreCase = true) && existing.tools.toSet() == tools.toSet())
        }
    }

    private fun containsSecret(text: String): Boolean {
        if (text.isBlank()) return false
        if (SECRET_PATTERNS.any { it.containsMatchIn(text) }) return true
        return PHONE_CANDIDATE.findAll(text).any { match ->
            match.value.filter { it.isDigit() }.length >= 10
        }
    }
}
