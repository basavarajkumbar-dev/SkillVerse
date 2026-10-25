package com.skillverse.app.data.content

import kotlinx.serialization.Serializable

/** Raw JSON shapes of the bundled curriculum (assets/content). Converted to domain models by [ContentValidator]. */

@Serializable
data class ContentManifestDto(
    val schemaVersion: Int,
    val contentVersion: Int,
    val packs: List<String>,
    val skills: String,
    val bosses: String,
    val projects: String,
)

@Serializable
data class ChallengePackDto(
    val schemaVersion: Int,
    val contentVersion: Int,
    val packId: String,
    val title: String,
    val sources: List<String> = emptyList(),
    val challenges: List<ChallengeDto>,
)

@Serializable
data class ChallengeDto(
    val id: String = "",
    val category: String = "",
    val skillId: String = "",
    val type: String = "",
    val difficulty: String = "",
    val estimatedSeconds: Int = 0,
    val title: String = "",
    val description: String = "",
    val instructions: String = "",
    val hints: List<String> = emptyList(),
    val explanation: String = "",
    val source: String = "",
    val prerequisites: List<String> = emptyList(),
    val code: String? = null,
    val examples: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val testCases: List<TestCaseDto> = emptyList(),
    val options: List<String> = emptyList(),
    val correctIndex: Int? = null,
    val acceptedAnswers: List<String> = emptyList(),
    val matchMode: String? = null,
    val steps: List<String> = emptyList(),
)

@Serializable
data class TestCaseDto(val input: String, val expected: String)

@Serializable
data class SkillTreeDto(
    val schemaVersion: Int,
    val contentVersion: Int,
    val unlockThresholdPercent: Int = 60,
    val nodes: List<SkillNodeDto>,
)

@Serializable
data class SkillNodeDto(
    val id: String,
    val title: String,
    val glyph: String = "",
    val description: String = "",
    val prerequisites: List<String> = emptyList(),
)

@Serializable
data class BossFileDto(val schemaVersion: Int, val contentVersion: Int, val bosses: List<BossDto>)

@Serializable
data class BossDto(val id: String, val title: String, val description: String, val challengeIds: List<String>)

@Serializable
data class ProjectFileDto(val schemaVersion: Int, val contentVersion: Int, val projects: List<ProjectDto>)

@Serializable
data class ProjectDto(
    val id: String,
    val title: String,
    val description: String,
    val source: String = "",
    val challengeIds: List<String>,
)
