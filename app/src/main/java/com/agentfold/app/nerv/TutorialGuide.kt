package com.agentfold.app.nerv

import android.content.Context

class TutorialGuide(context: Context) {

    companion object {
        private const val PREFS = "nerv_tutorial"
        private const val KEY_SEEN = "seen"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    data class TourStep(
        val screenKey: String,
        val glyph: String,
        val title: String,
        val body: String
    )

    data class KeyStep(
        val node: MagiEngine.NodeId,
        val nodeName: String,
        val providerName: String,
        val whereToGet: String,
        val whereToPaste: String,
        val howToTell: String
    )

    val tourSteps: List<TourStep> = listOf(
        TourStep(
            "home", "本部", "HOME — the front door",
            "This is HOME. The big clock is the time, the 8 tiles open " +
                "the 8 other screens, and the bars at the bottom are the " +
                "toxicity level. Tap any tile to go there, or the tabs " +
                "along the bottom."
        ),
        TourStep(
            "magi", "決議", "MAGI — the three nodes",
            "MAGI is the brain: MELCHIOR, BALTHASAR and CASPER vote on " +
                "things. Tap a node box to flip its vote. Two yes votes " +
                "mean APPROVED. The lock screen asks these same three " +
                "nodes whether to let you in."
        ),
        TourStep(
            "sync", "同調", "SYNC — how in tune NERV is",
            "SYNC shows how in tune NERV is with this phone, from the " +
                "real battery, CPU and memory. LIVE follows the phone; " +
                "MANUAL lets you move the slider yourself, like the old " +
                "HTML screen did."
        ),
        TourStep(
            "armor", "装甲", "TOOLS — notes, files and reports",
            "TOOLS holds your notes (in folders), your files, and your " +
                "reports — saved chats and debate logs you can export. " +
                "Everything here stays on this phone unless you tap SHARE."
        ),
        TourStep(
            "comms", "通信", "CHAT — talk to MAGI",
            "CHAT is where you talk to MAGI. Type a question and the " +
                "three nodes debate it, then answer together. The mic " +
                "button talks instead of typing. SEARCH CHAT finds an " +
                "old message, and TEACH MAGI saves something you teach it."
        ),
        TourStep(
            "tox", "毒性", "TOX — phone health",
            "TOX is the phone's health: battery strain, CPU and memory. " +
                "LIVE follows the real phone; INJECT and PURGE are just " +
                "temporary nudges. If a bar stays high, the phone is " +
                "working hard or running hot."
        ),
        TourStep(
            "timer", "起動", "TIMER — countdowns",
            "TIMER is countdowns. Pick one, START it, and NERV beeps " +
                "and shakes when it finishes. You can have up to 8, " +
                "with presets from 1 minute to 1 hour."
        ),
        TourStep(
            "map", "地球", "MAP — where the phone is",
            "MAP shows the phone's real location on the globe, with " +
                "coordinates. CENTRE ON ME turns the globe so the phone " +
                "is front and centre. Location never leaves this phone."
        ),
        TourStep(
            "setup", "設定", "SETUP — all the switches",
            "SETUP is every switch in one place: colours, sound, text " +
                "size, the MAGI node links and their keys, ports, voice, " +
                "notifications, offline mode, lock PIN, routines and the " +
                "tutorial itself. This is where the API keys go — next step."
        )
    )

    val keySteps: List<KeyStep> = listOf(
        KeyStep(
            MagiEngine.NodeId.MELCHIOR,
            "MELCHIOR·1", "Claude (Anthropic)",
            "Get the key at console.anthropic.com — sign in, open API " +
                "keys, and create one. It starts with sk-ant-.",
            "In NERV: SETUP > MAGI NODE LINKS > MELCHIOR > SET KEY. " +
                "Paste the key there. It goes straight into Android " +
                "Keystore — NERV never shows it on screen, not even here.",
            "The MELCHIOR line in SETUP says KEY SET. Then tap TEST " +
                "DEBATE below: a live answer means the key works. If it " +
                "says LOCAL DEMO, the key did not work or MELCHIOR is off."
        ),
        KeyStep(
            MagiEngine.NodeId.BALTHASAR,
            "BALTHASAR·2", "ChatGPT (OpenAI)",
            "Get the key at platform.openai.com — sign in, open API " +
                "keys, and create a new secret key. It starts with sk-.",
            "In NERV: SETUP > MAGI NODE LINKS > BALTHASAR > SET KEY. " +
                "Paste the key there. It goes straight into Android " +
                "Keystore — NERV never shows it on screen, not even here.",
            "The BALTHASAR line in SETUP says KEY SET. Then tap TEST " +
                "DEBATE below: a live answer means the key works. If it " +
                "says LOCAL DEMO, the key did not work or BALTHASAR is off."
        ),
        KeyStep(
            MagiEngine.NodeId.CASPER,
            "CASPER·3", "Grok (xAI)",
            "Get the key at console.x.ai — sign in, open API keys, and " +
                "create one. It starts with xai-.",
            "In NERV: SETUP > MAGI NODE LINKS > CASPER > SET KEY. " +
                "Paste the key there. It goes straight into Android " +
                "Keystore — NERV never shows it on screen, not even here.",
            "The CASPER line in SETUP says KEY SET. Then tap TEST " +
                "DEBATE below: a live answer means the key works. If it " +
                "says LOCAL DEMO, the key did not work or CASPER is off."
        )
    )

    fun isFirstRun(): Boolean = !prefs.getBoolean(KEY_SEEN, false)

    fun markSeen() {
        prefs.edit().putBoolean(KEY_SEEN, true).apply()
    }

    fun reset() {
        prefs.edit().putBoolean(KEY_SEEN, false).apply()
    }
}
