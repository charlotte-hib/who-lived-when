package dev.wholivedwhen.jobs

import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import dev.wholivedwhen.release.ReleaseCompiler
import dev.wholivedwhen.release.ReleaseReader
import dev.wholivedwhen.repository.RegionRepository
import java.nio.file.Path

/** [dir] is a release directory, in the format of the repository's `sample/`. */
@ConfigurationProperties("app.release")
data class ReleaseProperties(val dir: Path)

/** Compiles the release in `app.release.dir` and stores it, when the database is empty: on every start, with H2 in memory. */
@Component
@Order(1)
class ReleaseLoader(
    private val properties: ReleaseProperties,
    private val regions: RegionRepository,
    private val entityManager: EntityManager,
) : CommandLineRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun run(vararg args: String) {
        if (regions.count() > 0) return

        val release = ReleaseCompiler.compile(ReleaseReader.read(properties.dir))
        with(release) {
            // Every entity is new, so persist rather than merge, in the order that keeps references valid.
            listOf(regions, eras, people, lives, events, connections, moments, doors).flatten().forEach(entityManager::persist)
            log.info("Loaded the release in {}: {} people, {} moments", properties.dir, people.size, moments.size)
        }
    }
}
