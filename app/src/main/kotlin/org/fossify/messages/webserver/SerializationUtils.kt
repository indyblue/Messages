package org.fossify.messages.webserver

import org.json.JSONArray
import org.json.JSONObject
import kotlin.reflect.full.memberProperties

object SerializationUtils {

    fun <T> tryCatch(action: () -> T): Pair<T?, Exception?> {
        return try {
            Pair(action(), null)
        } catch (e: Exception) {
            Pair(null, e)
        }
    }

    fun serializeToJsonObj(data: Any?): Any? {
        return when (data) {
            null -> null
            is Collection<*> -> JSONArray(data.map { serializeToJsonObj(it) })
            is Map<*, *> -> JSONObject(data.mapValues { serializeToJsonObj(it.value) })
            is Number, is Boolean, is String -> data
            else -> JSONObject(
                data::class.memberProperties.associate { prop ->
                    prop.name to serializeToJsonObj(prop.getter.call(data))
                }
            )
        }
    }

    fun serializeToJson(data: Any?, exception: Exception? = null): String {
        val (value, error) = exception?.let { Pair(null, it) }
            ?: tryCatch { serializeToJsonObj(data) }
        return JSONObject(mapOf(
            "value" to value,
            "error" to error
        )).toString()
    }
}
