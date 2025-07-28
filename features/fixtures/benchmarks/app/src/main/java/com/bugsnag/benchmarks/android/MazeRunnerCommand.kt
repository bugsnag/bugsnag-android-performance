package com.bugsnag.benchmarks.android

import org.json.JSONObject

@JvmInline
value class MazeRunnerCommand(val json: JSONObject) {
    val id: String? get() = json.optString("uuid").takeIf { it.isNotBlank() }
    val action: String? get() = json.optString("action").takeIf { it.isNotBlank() }
    val isNoOp: Boolean get() = action == null || action == "noop"

    inline operator fun <reified R> get(key: String): R? = json.opt(key) as? R

    override fun toString(): String = json.toString()
}
