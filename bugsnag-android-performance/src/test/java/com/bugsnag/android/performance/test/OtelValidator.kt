package com.bugsnag.android.performance.test

import net.jimblackler.jsonschemafriend.SchemaStore
import net.jimblackler.jsonschemafriend.Validator


object OtelValidator {
    private val schemaStore = SchemaStore()
    private val traceSchema =
        schemaStore.loadSchema(OtelValidator::class.java.getResourceAsStream("/otel_trace_schema.json"))

    fun assertTraceData(json: ByteArray) {
        Validator().validate(traceSchema, json.inputStream())
    }
}