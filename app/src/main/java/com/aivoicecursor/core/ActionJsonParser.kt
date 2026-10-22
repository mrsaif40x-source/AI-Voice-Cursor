package com.aivoicecursor.core

import org.json.JSONArray
import org.json.JSONObject

/** Strict JSON -> ActionPlan parser. Unknown action types make the whole plan invalid. */
object ActionJsonParser {

    fun parse(raw: String): Result<ActionPlan> = runCatching {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        require(start in 0 until end) { "No JSON object found" }
        val root = JSONObject(raw.substring(start, end + 1))
        val arr = root.optJSONArray("actions") ?: throw IllegalArgumentException("Missing 'actions' array")
        val actions = (0 until arr.length()).map { i ->
            val o = arr.optJSONObject(i) ?: throw IllegalArgumentException("Action #${i + 1} is not an object")
            val name = o.optString("type").trim().uppercase()
            val type = ActionType.entries.firstOrNull { it.name == name }
                ?: throw IllegalArgumentException("Unknown action type: '$name'")
            AgentAction(
                type = type,
                target = o.str("target"),
                text = o.str("text"),
                direction = o.str("direction"),
                ms = if (o.has("ms") && !o.isNull("ms")) o.optLong("ms") else null
            )
        }
        ActionPlan(actions)
    }

    fun toJson(plan: ActionPlan): String {
        val arr = JSONArray()
        plan.actions.forEach { a ->
            val o = JSONObject().put("type", a.type.name)
            a.target?.let { o.put("target", it) }
            a.text?.let { o.put("text", it) }
            a.direction?.let { o.put("direction", it) }
            a.ms?.let { o.put("ms", it) }
            arr.put(o)
        }
        return JSONObject().put("actions", arr).toString()
    }

    private fun JSONObject.str(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null
}
