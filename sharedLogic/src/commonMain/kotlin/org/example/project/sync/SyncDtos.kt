package org.example.project.sync

import kotlinx.serialization.Serializable

// Dokumentformatene i Firestore. Feltnavn = kolonnenavn lokalt.
// deleted/resolved o.l. lagres som Long (0/1) for 1:1-mapping mot SQLite.

@Serializable
data class TemplateDto(
    val id: String,
    val name: String,
    val type: String,
    val parentId: String? = null,
    val sortOrder: Long = 0,
    val version: Long = 1,
    val updatedAt: Long = 0,
    val deleted: Long = 0,
)

@Serializable
data class ItemDto(
    val id: String,
    val templateId: String,
    val title: String,
    val description: String? = null,
    val requiresValue: Long = 0,
    val unit: String? = null,
    val minValue: Double? = null,
    val maxValue: Double? = null,
    val sortOrder: Long = 0,
    val updatedAt: Long = 0,
    val deleted: Long = 0,
)

@Serializable
data class UserDto(
    val id: String,
    val name: String,
    val role: String,
    val updatedAt: Long = 0,
    val deleted: Long = 0,
)

@Serializable
data class AmbulanceDto(
    val id: String,
    val callSign: String,
    val registrationNumber: String,
    val updatedAt: Long = 0,
    val deleted: Long = 0,
)

@Serializable
data class LinkDto(
    val id: String,
    val title: String,
    val url: String,
    val sortOrder: Long = 0,
    val updatedAt: Long = 0,
    val deleted: Long = 0,
)

@Serializable
data class RunDto(
    val id: String,
    val templateId: String,
    val ambulanceId: String,
    val userId: String? = null,
    val createdAt: Long = 0,
    val completedAt: Long? = null,
    val status: String,
    val comment: String? = null,
    val updatedAt: Long = 0,
)

@Serializable
data class ResponseDto(
    val id: String,
    val checklistRunId: String,
    val itemId: String,
    val result: String,
    val comment: String? = null,
    val reading: String? = null,
    val checkedAt: Long = 0,
    val resolved: Long = 0,
    val resolvedAt: Long? = null,
    val resolvedReading: String? = null,
    val resolvedVia: String? = null,
    val resolvedByRunId: String? = null,
    val resolvedByUserId: String? = null,
    val updatedAt: Long = 0,
)
