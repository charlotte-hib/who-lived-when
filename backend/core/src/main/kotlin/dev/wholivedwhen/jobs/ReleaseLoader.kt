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
import java.nio.file.Path

/** [dir] is a release directory, in the format of the repository's `sample/`. */
@ConfigurationProperties("app.release")
data class ReleaseProperties(val dir: Path)

/**
 * Compiles the release in `app.release.dir` and stores it on every start, in place of whatever the database holds.
 * One transaction: until it commits, the API keeps waiting on the emptied tables rather than reading them half-loaded.
 */
@Component
@Order(1)
class ReleaseLoader(
    private val properties: ReleaseProperties,
    private val entityManager: EntityManager,
) : CommandLineRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun run(vararg args: String) {
        val release = ReleaseCompiler.compile(ReleaseReader.read(properties.dir))

        // Every table of the site model (core's db/migration). Their id sequences start over, so ids are the same on
        // every start. A table left out of this list but pointing at one in it makes the statement fail.
        entityManager.createNativeQuery(
            "truncate table region, era, person, life, event, event_participant, connection, moment, story_card, " +
                "door, door_face restart identity"
        ).executeUpdate()
        with(release) {
            // Every entity is new, so persist rather than merge, in the order that keeps references valid.
            listOf(regions, eras, people, lives, events, connections, moments, doors).flatten().forEach(entityManager::persist)
            log.info("Loaded the release in {}: {} people, {} moments", properties.dir, people.size, moments.size)
        }
    }
}
