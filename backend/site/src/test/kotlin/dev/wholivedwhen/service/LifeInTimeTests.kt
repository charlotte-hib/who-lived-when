package dev.wholivedwhen.service

import org.junit.jupiter.api.Test
import dev.wholivedwhen.domain.Domain
import dev.wholivedwhen.domain.Era
import dev.wholivedwhen.domain.Event
import dev.wholivedwhen.domain.Person
import dev.wholivedwhen.domain.Region
import dev.wholivedwhen.api.model.LifeLineKindDto
import kotlin.test.assertEquals

class LifeInTimeTests {

    private val rome = Region("IT", "Italy")
    private val republic = Era("it-republic", rome, "Roman Republic", "Roman Republic", -509, -27)
    private val empire = Era("it-empire", rome, "Roman Empire", "Roman Empire", -27, 476)
    private val augustus = Person("augustus", "augustus", "Augustus", -63, 14, false, rome, Domain.POWER, "emperor", null)

    @Test
    fun `ages skip the year 0 that never existed`() {
        val lines = lifeInTime(augustus, listOf(republic, empire), emptyList())
        assertEquals(listOf(LifeLineKindDto.BIRTH, LifeLineKindDto.ERA, LifeLineKindDto.DEATH), lines.map { it.kind })
        assertEquals("Born under the Roman Republic", lines[0].text)
        assertEquals(36, lines[1].age)
        // 63 BCE to 14 CE is 76 years, not 77.
        assertEquals(76, lines[2].age)
    }

    @Test
    fun `their own events carry their role and others are capped`() {
        val own = Event("actium", empire, "Battle of Actium", -31, "", "").participant(augustus, "victor")
        val others = (1..6).map { Event("e$it", empire, "Event $it", -40 + it, "", "") }
        val lines = lifeInTime(augustus, emptyList(), others + own)
        assertEquals("victor", lines.single { it.kind == LifeLineKindDto.OWN_EVENT }.role)
        assertEquals(4, lines.count { it.kind == LifeLineKindDto.EVENT })
    }
}
