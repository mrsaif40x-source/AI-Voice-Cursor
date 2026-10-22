package com.aivoicecursor.core

enum class Safety { ALLOW, CONFIRM, BLOCK }

/** Permission/safety layer between validation and execution. */
object SafetyChecker {

    private val sensitive = listOf(
        "pay", "purchase", "buy", "order", "checkout", "delete", "remove", "uninstall",
        "erase", "format", "reset", "send", "transfer", "withdraw", "logout", "log out",
        "sign out", "install",
        "পেমেন্ট", "কিনু", "কিনো", "অর্ডার", "ডিলিট", "মুছ", "আনইনস্টল", "পাঠা",
        "ট্রান্সফার", "লগআউট", "ইনস্টল", "রিসেট"
    )
    private val secrets = listOf(
        "password", "passcode", "pin", "otp", "cvv", "card number",
        "পাসওয়ার্ড", "পিন", "ওটিপি"
    )

    fun assess(a: AgentAction): Safety = when (a.type) {
        ActionType.TYPE ->
            if (a.target != null && matches(a.target, secrets, whole = true)) Safety.BLOCK else Safety.ALLOW
        ActionType.CLICK, ActionType.LONG_CLICK, ActionType.FOCUS, ActionType.OPEN_APP ->
            if (a.target != null && isSensitiveLabel(a.target)) Safety.CONFIRM else Safety.ALLOW
        ActionType.SCREENSHOT -> Safety.CONFIRM
        else -> Safety.ALLOW
    }

    fun isSensitiveLabel(label: String): Boolean = matches(label, sensitive, whole = false)

    private fun matches(text: String, keywords: List<String>, whole: Boolean): Boolean {
        val t = text.lowercase()
        return keywords.any { kw ->
            if (kw.all { it.code < 128 }) {
                Regex("\\b" + Regex.escape(kw) + (if (whole) "\\b" else "")).containsMatchIn(t)
            } else t.contains(kw)
        }
    }
}
