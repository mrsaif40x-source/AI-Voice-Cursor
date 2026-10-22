package com.aivoicecursor.core

/**
 * Rule-based Bengali/English command parser. Used as a deterministic fallback
 * when the local LLM is not installed or returns an invalid plan.
 * Returns null when the command is not understood.
 */
object VoiceCommandParser {
    private val I = RegexOption.IGNORE_CASE

    private val scrollUp = Regex("^(?:scroll up|স্ক্রল আপ(?: করো)?|উপরে স্ক্রল(?: করো)?|উপরে যাও)$", I)
    private val scrollDown = Regex("^(?:scroll down|স্ক্রল ডাউন(?: করো)?|নিচে স্ক্রল(?: করো)?|নিচে যাও)$", I)
    private val back = Regex("^(?:go back|back|পিছনে যাও|পেছনে যাও|ফিরে যাও|ব্যাক(?: করো)?)$", I)
    private val home = Regex("^(?:go home|home|হোমে যাও|হোম(?: স্ক্রিনে যাও)?)$", I)
    private val read = Regex("^(?:read (?:the )?screen|স্ক্রিন পড়ো|স্ক্রিন পড়)$", I)
    private val shot = Regex("^(?:take (?:a )?screenshot|screenshot|স্ক্রিনশট(?: নাও| নিন)?)$", I)

    private val longClickBn = Regex("^(.+?)\\s+(?:লং ক্লিক|লং প্রেস)(?:\\s+(?:করো|কর|করুন))?$", I)
    private val longClickEn = Regex("^long (?:click|press|tap)(?: on)?\\s+(.+)$", I)

    private val typeThenClickBn = Regex("^(.+?)\\s+(?:লিখে|লেখে|লিখিয়ে)\\s+(.+)$", I)
    private val typeThenClickEn = Regex("^(?:type|write)\\s+(.+?)\\s+and\\s+(?:click|tap|press)\\s+(.+)$", I)

    private val typeBn = Regex("^(.+?)\\s+(?:লিখো|লেখো|লিখ|লিখুন|টাইপ করো|টাইপ কর)$", I)
    private val typeEn = Regex("^(?:type|write)\\s+(.+)$", I)

    private val openEn = Regex("^(?:open|launch|start)\\s+(.+)$", I)
    private val openBn = Regex("^(.+?)\\s+(?:খোলো|খুলো|খুলুন|ওপেন করো|ওপেন কর|চালু করো)$", I)

    private val clickBn = Regex("^(.+?)\\s+(?:ক্লিক|চাপো|চাপ|টাচ|ট্যাপ)(?:\\s+(?:করো|কর|করুন|দাও))?$", I)
    private val clickEn = Regex("^(?:click|tap|press)(?: on)?\\s+(.+)$", I)

    private val tail = Regex(
        "(?:\\s+(?:করো|কর|করুন|দাও|দিন|ক্লিক|চাপো|চাপ|টাচ|click|tap|press|" +
            "বক্সে|বক্স|বাটনে|বাটন|বোতামে|বোতাম|button|box|field))+$", I
    )
    private val head = Regex("^(?:the|on)\\s+", I)

    fun parse(input: String): ActionPlan? {
        val s = input.trim().trimEnd('.', '।', '!', '?').replace(Regex("\\s+"), " ")
        if (s.isEmpty()) return null

        when {
            scrollUp.matches(s) -> return one(AgentAction(ActionType.SCROLL_UP))
            scrollDown.matches(s) -> return one(AgentAction(ActionType.SCROLL_DOWN))
            back.matches(s) -> return one(AgentAction(ActionType.BACK))
            home.matches(s) -> return one(AgentAction(ActionType.HOME))
            read.matches(s) -> return one(AgentAction(ActionType.READ_SCREEN))
            shot.matches(s) -> return one(AgentAction(ActionType.SCREENSHOT))
        }

        (longClickBn.find(s) ?: longClickEn.find(s))?.let { m ->
            clean(m.groupValues[1])?.let { return one(AgentAction(ActionType.LONG_CLICK, target = it)) }
        }

        (typeThenClickBn.find(s) ?: typeThenClickEn.find(s))?.let { m ->
            val text = unquote(m.groupValues[1])
            val target = clean(m.groupValues[2])
            if (text.isNotEmpty() && target != null) {
                return ActionPlan(
                    listOf(
                        AgentAction(ActionType.TYPE, text = text),
                        AgentAction(ActionType.CLICK, target = target)
                    )
                )
            }
        }

        (typeBn.find(s) ?: typeEn.find(s))?.let { m ->
            val text = unquote(m.groupValues[1])
            if (text.isNotEmpty()) return one(AgentAction(ActionType.TYPE, text = text))
        }

        openEn.find(s)?.let { m -> return openPlan(m.groupValues[1]) }
        openBn.find(s)?.let { m -> return openPlan(m.groupValues[1]) }

        (clickBn.find(s) ?: clickEn.find(s))?.let { m ->
            clean(m.groupValues[1])?.let { return one(AgentAction(ActionType.CLICK, target = it)) }
        }
        return null
    }

    private fun openPlan(raw: String): ActionPlan? {
        val v = unquote(raw)
        if (v.isEmpty()) return null
        return if (v.startsWith("http://", true) || v.startsWith("https://", true))
            one(AgentAction(ActionType.OPEN_URL, text = v))
        else one(AgentAction(ActionType.OPEN_APP, target = v))
    }

    private fun one(a: AgentAction) = ActionPlan(listOf(a))

    private fun clean(raw: String): String? =
        unquote(raw).replace(tail, "").replace(head, "").trim().ifEmpty { null }

    private fun unquote(s: String) = s.trim().trim('"', '\'', '“', '”', '‘', '’').trim()
}
