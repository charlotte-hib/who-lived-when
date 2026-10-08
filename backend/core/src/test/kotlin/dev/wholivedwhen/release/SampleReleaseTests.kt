package dev.wholivedwhen.release

import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals

/** The repository's sample/ release, which the site's tests, local runs and the Docker image serve. */
class SampleReleaseTests {

    private val sample = Path.of(System.getProperty("sample.dir") ?: error("Run with Gradle, which sets sample.dir"))

    @Test
    fun `sample compiles`() {
        val release = ReleaseCompiler.compile(ReleaseReader.read(sample))

        assertEquals(release.people.size, release.people.map { it.slug }.toSet().size)
        assertEquals("fr-celtic-gaul", release.events.single { it.id == "alesia" }.era.id)
    }
}
