package com.skillverse.app.data

import com.skillverse.app.TestContent
import com.skillverse.app.data.content.BundledContentRepository
import com.skillverse.app.data.content.ContentLoadResult
import com.skillverse.app.data.content.ContentSource
import com.skillverse.app.domain.model.Category
import com.skillverse.app.domain.model.ChallengeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException

class ContentRepositoryTest {
    private val c = TestContent.curriculum

    @Test fun bundleLoadsWithoutDiagnostics() {
        assertTrue("diagnostics: ${c.diagnostics}", c.diagnostics.isEmpty())
        assertEquals(100, c.challenges.size)
        assertEquals(10, c.skills.size)
        assertEquals(5, c.bosses.size)
        assertEquals(5, c.projects.size)
    }

    @Test fun curriculumMeetsCategoryTargets() {
        fun count(vararg cats: Category) = c.challenges.count { it.category in cats }
        assertTrue(count(Category.PYTHON) >= 35)
        assertTrue(count(Category.DEBUGGING, Category.LOGIC) >= 20)
        assertTrue(count(Category.TEST_AUTOMATION) >= 15)
        assertTrue(count(Category.API_TESTING) >= 10)
        assertTrue(count(Category.LINUX, Category.GIT) >= 10)
        assertTrue(count(Category.EMBEDDED, Category.DEVOPS) >= 10)
    }

    @Test fun everyChallengeIsComplete() {
        c.challenges.forEach { ch ->
            assertTrue(ch.id, ch.hints.isNotEmpty() && ch.explanation.isNotBlank() && ch.source.isNotBlank())
            assertTrue(ch.id, ch.xp > 0 && ch.estimatedSeconds > 0)
            assertTrue(ch.id, ch.skillId in c.skillsById)
        }
        assertTrue(ChallengeType.entries.all { t -> c.challenges.any { it.type == t } })
        assertTrue(Category.entries.all { cat -> c.challenges.any { it.category == cat } })
    }

    private fun overriding(vararg files: Pair<String, String>): ContentSource {
        val map = files.toMap()
        return ContentSource { name -> map[name] ?: TestContent.source.read(name) }
    }

    @Test fun malformedChallengesAreRejectedWithDiagnosticsWhileOthersLoad() = runTest {
        val badPack = """
            {"schemaVersion":1,"contentVersion":1,"packId":"python","title":"Python","challenges":[
              {"id":"bad","category":"PYTHON","skillId":"python_foundations","type":"MULTIPLE_CHOICE","difficulty":"EASY",
               "estimatedSeconds":60,"title":"t","description":"d","instructions":"i","hints":["h"],"explanation":"e",
               "options":["a","b"],"correctIndex":5},
              {"id":"ok-001","category":"PYTHON","skillId":"python_foundations","type":"MULTIPLE_CHOICE","difficulty":"EASY",
               "estimatedSeconds":60,"title":"t","description":"d","instructions":"i","hints":["h"],"explanation":"e",
               "source":"s","options":["a","b"],"correctIndex":1},
              {"id":"zzz-002","category":"NOPE","skillId":"nowhere","type":"FILL_BLANK","difficulty":"EASY",
               "estimatedSeconds":60,"title":"t","description":"d","instructions":"i","hints":["h"],"explanation":"e",
               "code":"no blank here","acceptedAnswers":["x"],"matchMode":"CODE"}
            ]}
        """.trimIndent()
        val result = BundledContentRepository(overriding("pack_python.json" to badPack), Dispatchers.Unconfined).load()
        result as ContentLoadResult.Ready
        val d = result.curriculum.diagnostics
        assertTrue(d.any { it.contains("bad") && it.contains("abc-001") })
        assertTrue(d.any { it.contains("correctIndex out of range") })
        assertTrue(d.any { it.contains("unknown category") })
        assertTrue(d.any { it.contains("exactly one ____") })
        assertTrue("ok-001" in result.curriculum.byId)
        assertTrue("bad" !in result.curriculum.byId)
        // Bosses and projects referencing removed Python items are rejected, not crashed on.
        assertTrue(d.any { it.startsWith("bosses.json") })
    }

    @Test fun brokenJsonPackIsSkipped() = runTest {
        val result = BundledContentRepository(overriding("pack_linux_git.json" to "{not json"), Dispatchers.Unconfined).load()
        result as ContentLoadResult.Ready
        assertTrue(result.curriculum.diagnostics.any { it.contains("pack_linux_git.json") && it.contains("malformed") })
        assertEquals(90, result.curriculum.challenges.size)
    }

    @Test fun missingManifestFailsGracefully() = runTest {
        val source = ContentSource { name -> if (name == "manifest.json") throw FileNotFoundException(name) else TestContent.source.read(name) }
        val result = BundledContentRepository(source, Dispatchers.Unconfined).load()
        result as ContentLoadResult.Failed
        assertTrue(result.message.contains("missing"))
    }

    @Test fun unsupportedSchemaIsRejected() = runTest {
        val manifest = """{"schemaVersion":99,"contentVersion":1,"packs":[],"skills":"skills.json","bosses":"bosses.json","projects":"projects.json"}"""
        val result = BundledContentRepository(overriding("manifest.json" to manifest), Dispatchers.Unconfined).load()
        assertTrue(result is ContentLoadResult.Failed)
    }
}
