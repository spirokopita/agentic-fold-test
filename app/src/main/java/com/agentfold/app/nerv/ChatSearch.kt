package com.agentfold.app.nerv

object ChatSearch {

    enum class HitKind { MESSAGE, LOG }

    data class Hit(

        val entryIndex: Int,

        val kind: HitKind,

        val snippet: String
    )

    private const val MAX_HITS = 50
    private const val SNIPPET_RADIUS = 40

    fun search(
        entries: List<ChatStore.Entry>,
        query: String,
        maxHits: Int = MAX_HITS
    ): List<Hit> {
        val terms = query.trim().lowercase().split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
        if (terms.isEmpty()) return emptyList()
        val hits = mutableListOf<Hit>()
        entries.forEachIndexed { index, entry ->
            if (entry.deleted) return@forEachIndexed
            val messageText = "${entry.sender} ${entry.text}"
            val messageLower = messageText.lowercase()
            val messageMatches = terms.all { messageLower.contains(it) }

            val logLineIndex = entry.log.indexOfFirst { line ->
                terms.all { line.lowercase().contains(it) }
            }
            val logMatches = logLineIndex >= 0
            if (messageMatches) {
                hits.add(Hit(index, HitKind.MESSAGE, snippet(messageText, terms[0])))
            }
            if (logMatches) {
                hits.add(Hit(index, HitKind.LOG, snippet(entry.log[logLineIndex], terms[0])))
            }
            if (hits.size >= maxHits) return hits
        }
        return hits
    }

    private fun snippet(text: String, term: String): String {
        val at = text.lowercase().indexOf(term.lowercase())
        if (at < 0) return text.take(2 * SNIPPET_RADIUS)
        val start = (at - SNIPPET_RADIUS).coerceAtLeast(0)
        val end = (at + term.length + SNIPPET_RADIUS).coerceAtMost(text.length)
        val cut = text.substring(start, end).replace(Regex("\\s+"), " ").trim()
        val prefix = if (start > 0) "… " else ""
        val suffix = if (end < text.length) " …" else ""
        return prefix + cut + suffix
    }
}
