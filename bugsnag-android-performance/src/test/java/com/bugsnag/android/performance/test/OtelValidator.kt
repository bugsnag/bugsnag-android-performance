package com.bugsnag.android.performance.test

import net.jimblackler.jsonschemafriend.SchemaStore
import net.jimblackler.jsonschemafriend.Validator
import java.net.URI

object OtelValidator {
    private val schemaStore = SchemaStore().apply {
        store(
            URI.create("http://json-schema.org/draft-04/schema"),
            OtelValidator::class.java.getResource("/json_schema4.json"),
        )
    }

    private val traceSchema = schemaStore.loadSchema(
        OtelValidator::class.java.getResourceAsStream("/otel_trace_schema.json"),
    )

    fun assertTraceDataValid(json: ByteArray) {
        Validator().validate(traceSchema, json.inputStream())
    }
}
