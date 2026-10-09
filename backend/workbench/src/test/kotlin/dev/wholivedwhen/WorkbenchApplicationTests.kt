package dev.wholivedwhen

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import
import dev.wholivedwhen.drafting.DraftSources
import dev.wholivedwhen.drafting.StoryDraftJob
import dev.wholivedwhen.repository.MomentRepository
import dev.wholivedwhen.testing.PostgresTestConfiguration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Starts as `./gradlew :workbench:bootRun` does without a moment to draft: it loads the repository's `sample/`. */
@SpringBootTest(properties = ["app.wikipedia.enrich=false"])
@Import(PostgresTestConfiguration::class)
class WorkbenchApplicationTests(
    @Autowired private val moments: MomentRepository,
    @Autowired private val context: ApplicationContext,
) {

    @Test
    fun `loads the release, and drafts nothing unless asked for a moment`() {
        assertEquals(10, moments.count())
        assertTrue(context.getBeansOfType(DraftSources::class.java).isNotEmpty())
        assertTrue(context.getBeansOfType(StoryDraftJob::class.java).isEmpty())
    }
}
