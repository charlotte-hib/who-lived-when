package dev.wholivedwhen.release

import tools.jackson.core.JacksonException
import tools.jackson.databind.DeserializationFeature
import tools.jackson.module.kotlin.jacksonMapperBuilder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readLines

/**
 * Reads a release directory: a JSON Lines file per kind of record, sorted by id, and a JSON file per moment.
 * Strict, so that a typo fails here rather than going missing: unknown fields, missing fields, duplicates and
 * lines out of order are errors that name the file and the line.
 */
object ReleaseReader {

    private val mapper = jacksonMapperBuilder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build()

    fun read(dir: Path): Release {
        if (!dir.isDirectory()) throw InvalidReleaseException("No release directory at ${dir.toAbsolutePath()}")
        return Release(
            regions = lines(dir, "regions") { listOf(it.id) },
            eras = lines(dir, "eras") { listOf(it.id) },
            people = lines(dir, "people") { listOf(it.id) },
            lives = lines(dir, "lives") { listOf(it.id) },
            events = lines(dir, "events") { listOf(it.id) },
            // Connections have no id: they are sorted by their two people, then by kind.
            connections = lines(dir, "connections") { it.people + it.kind },
            moments = moments(dir.resolve("moments")),
        )
    }

    private inline fun <reified T : Any> lines(dir: Path, name: String, key: (T) -> List<String>): List<T> {
        val file = "$name.jsonl"
        val path = dir.resolve(file)
        if (!Files.isRegularFile(path)) throw InvalidReleaseException("$file is missing")
        var previous: List<String>? = null
        return path.readLines().mapIndexed { index, line ->
            val where = "$file:${index + 1}"
            val record = parse(where) { mapper.readValue(line, T::class.java) }
            val current = key(record)
            previous?.let {
                val order = compareKeys(it, current)
                if (order == 0) throw InvalidReleaseException("$where: ${current.joinToString(" ")} appears twice")
                if (order > 0) throw InvalidReleaseException("$where: ${current.joinToString(" ")} is out of order")
            }
            previous = current
            record
        }
    }

    private fun moments(dir: Path): List<MomentRecord> {
        if (!dir.isDirectory()) throw InvalidReleaseException("moments/ is missing")
        val files = Files.list(dir).use { paths -> paths.filter { it.extension == "json" }.sorted().toList() }
        return files.map { path ->
            val where = "moments/${path.name}"
            val moment = parse(where) { Files.newInputStream(path).use { mapper.readValue(it, MomentRecord::class.java) } }
            if (moment.id != path.nameWithoutExtension) throw InvalidReleaseException("$where: its id is ${moment.id}")
            moment
        }
    }

    private fun <T> parse(where: String, read: () -> T): T =
        try {
            read()
        } catch (e: JacksonException) {
            throw InvalidReleaseException("$where: ${e.originalMessage}")
        }

    private fun compareKeys(a: List<String>, b: List<String>): Int =
        a.zip(b).map { (x, y) -> x.compareTo(y) }.firstOrNull { it != 0 } ?: a.size.compareTo(b.size)
}
