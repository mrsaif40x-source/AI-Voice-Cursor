package com.aivoicecursor.ai

object PromptBuilder {
    private const val INSTRUCTIONS = """You control an Android phone through a strict JSON protocol.
Reply with exactly ONE JSON object and nothing else.
Schema: {"actions":[{"type":"CLICK","target":"Search"},{"type":"TYPE","text":"Python tutorial"}]}
Allowed types: CLICK, LONG_CLICK, TYPE, SCROLL_UP, SCROLL_DOWN, SWIPE (direction UP|DOWN|LEFT|RIGHT), BACK, HOME, OPEN_APP (target = app name), OPEN_URL (text = http/https URL), FOCUS, WAIT (ms), READ_SCREEN, SCREENSHOT, SEARCH_UI, CONFIRM (text = question).
Rules:
- CLICK/LONG_CLICK/FOCUS/SEARCH_UI: copy "target" from a hint, text, desc or id shown on the current screen.
- TYPE: put the text to type in "text". Never type passwords, PINs or OTPs.
- Use at most 5 actions. No other fields, no explanations, no markdown.
- If the command cannot be done with the allowed types, reply {"actions":[]}."""

    fun build(command: String, screenSummary: String): String =
        "<start_of_turn>user\n$INSTRUCTIONS\n\nCurrent screen:\n$screenSummary\n\n" +
            "User command (Bengali or English): $command<end_of_turn>\n<start_of_turn>model\n"
}
