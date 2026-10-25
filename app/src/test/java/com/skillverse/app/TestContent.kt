package com.skillverse.app

import com.skillverse.app.data.content.BundledContentRepository
import com.skillverse.app.data.content.ContentLoadResult
import com.skillverse.app.data.content.ContentSource
import com.skillverse.app.domain.model.Curriculum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File

/** Loads the real bundled curriculum from the source tree (unit tests run with the module as working dir). */
object TestContent {
    private val dir: File by lazy {
        listOf(File("src/main/assets/content"), File("app/src/main/assets/content")).first { it.isDirectory }
    }

    val source = ContentSource { name -> File(dir, name).readText() }

    val curriculum: Curriculum by lazy {
        when (val r = runBlocking { BundledContentRepository(source, Dispatchers.Unconfined).load() }) {
            is ContentLoadResult.Ready -> r.curriculum
            is ContentLoadResult.Failed -> error("Bundled content failed to load: ${r.message} ${r.diagnostics}")
        }
    }
}
