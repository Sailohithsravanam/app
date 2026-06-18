package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ChatRequest(
    @Json(name = "prompt") val prompt: String
)

@JsonClass(generateAdapter = true)
data class ChatResponse(
    @Json(name = "reply") val reply: String
)

@JsonClass(generateAdapter = true)
data class ChatMessage(
    @Json(name = "role") val role: String, // "user" or "assistant"
    @Json(name = "content") val content: String,
    @Json(name = "timestamp") val timestamp: Long
)
