package com.skillverse.app.data.content

import android.content.res.AssetManager
import com.skillverse.app.domain.model.Curriculum
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException

/** Reads raw content files by name. Lets tests load the same JSON from the source tree. */
fun interface ContentSource {
    @Throws(IOException::class)
    fun read(name: String): String
}

class AssetContentSource(private val assets: AssetManager, private val directory: String = "content") : ContentSource {
    override fun read(name: String): String = assets.open("$directory/$name").bufferedReader(Charsets.UTF_8).use { it.readText() }
}

sealed interface ContentLoadResult {
    data class Ready(val curriculum: Curriculum) : ContentLoadResult

    /** The bundle could not be used at all. [message] is user-safe; [diagnostics] go to the diagnostics screen. */
    data class Failed(val message: String, val diagnostics: List<String>) : ContentLoadResult
}

interface ContentRepository {
    suspend fun load(): ContentLoadResult
}

class BundledContentRepository(
    private val source: ContentSource,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ContentRepository {
    private val mutex = Mutex()
    private var cached: ContentLoadResult? = null

    private val json = Json {
        ignoreUnknownKeys = true // authoring-only fields such as "verify" are ignored
        isLenient = false
    }

    override suspend fun load(): ContentLoadResult = mutex.withLock {
        cached ?: withContext(dispatcher) { parse() }.also { cached = it }
    }

    private fun parse(): ContentLoadResult {
        val diagnostics = mutableListOf<String>()
        return try {
            val manifest = json.decodeFromString<ContentManifestDto>(source.read("manifest.json"))
            if (manifest.schemaVersion != ContentValidator.SUPPORTED_SCHEMA_VERSION) {
                return ContentLoadResult.Failed(
                    "This content bundle needs a newer version of SkillVerse.",
                    listOf("manifest.json: schemaVersion ${manifest.schemaVersion} is not supported"),
                )
            }
            val skillsDto = json.decodeFromString<SkillTreeDto>(source.read(manifest.skills))
            val skills = ContentValidator.validateSkills(skillsDto).also { diagnostics += it.diagnostics }.value
            val skillIds = skills.map { it.id }.toSet()

            val seen = mutableSetOf<String>()
            val challenges = manifest.packs.flatMap { packName ->
                try {
                    val pack = json.decodeFromString<ChallengePackDto>(source.read(packName))
                    if (pack.schemaVersion != ContentValidator.SUPPORTED_SCHEMA_VERSION) {
                        diagnostics += "$packName: unsupported schemaVersion ${pack.schemaVersion}; pack skipped"
                        emptyList()
                    } else {
                        ContentValidator.validateChallenges(packName, pack.challenges, skillIds, seen)
                            .also { diagnostics += it.diagnostics }.value
                    }
                } catch (e: SerializationException) {
                    diagnostics += "$packName: malformed JSON (${e.message?.take(200)}); pack skipped"
                    emptyList()
                } catch (e: IOException) {
                    diagnostics += "$packName: missing or unreadable; pack skipped"
                    emptyList()
                }
            }
            diagnostics += ContentValidator.prerequisiteProblems(challenges)
            val ids = challenges.map { it.id }.toSet()
            val bosses = ContentValidator.validateBosses(json.decodeFromString(source.read(manifest.bosses)), ids)
                .also { diagnostics += it.diagnostics }.value
            val projects = ContentValidator.validateProjects(json.decodeFromString(source.read(manifest.projects)), ids)
                .also { diagnostics += it.diagnostics }.value

            if (challenges.isEmpty() || skills.isEmpty()) {
                ContentLoadResult.Failed("No usable challenges were found in this build.", diagnostics)
            } else {
                ContentLoadResult.Ready(
                    Curriculum(
                        contentVersion = manifest.contentVersion,
                        challenges = challenges,
                        skills = skills,
                        unlockThresholdPercent = skillsDto.unlockThresholdPercent.coerceIn(1, 100),
                        bosses = bosses,
                        projects = projects,
                        diagnostics = diagnostics.toList(),
                    ),
                )
            }
        } catch (e: SerializationException) {
            ContentLoadResult.Failed("The learning content is damaged. Reinstall the app to repair it.", diagnostics + "malformed JSON: ${e.message?.take(300)}")
        } catch (e: IOException) {
            ContentLoadResult.Failed("The learning content is missing. Reinstall the app to repair it.", diagnostics + "missing file: ${e.message}")
        } catch (e: IllegalArgumentException) {
            ContentLoadResult.Failed("The learning content is damaged. Reinstall the app to repair it.", diagnostics + "invalid content: ${e.message?.take(300)}")
        }
    }
}
