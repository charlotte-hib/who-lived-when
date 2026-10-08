package dev.wholivedwhen.web

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/** Runs against the release in the repository's `sample/`. */
@SpringBootTest(properties = ["app.wikipedia.enrich=false"])
@AutoConfigureMockMvc
class PersonApiTests(@Autowired private val mockMvc: MockMvc) {

    @Test
    fun `a person is shown in the world around them in the year asked for`() {
        mockMvc.get("/api/people/emile-zola?year=1875").andExpect {
            status { isOk() }
            jsonPath("$.year") { value(1875) }
            jsonPath("$.age") { value(35) }
            jsonPath("$.worldMoment.id") { value("paris-1870s") }
            jsonPath("$.world.arts") { exists() }
            jsonPath("$.aroundPeople[?(@.slug == 'victor-hugo')]") { exists() }
            jsonPath("$.aroundPeople[?(@.slug == 'emile-zola')]") { doesNotExist() }
            jsonPath("$.elsewhere[*].regionCode") { value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("FR"))) }
            jsonPath("$.moments[*].id") { value(org.hamcrest.Matchers.hasItems("paris-1870s", "paris-1890s")) }
        }
    }

    @Test
    fun `their life in their time lists regimes and events with their age`() {
        mockMvc.get("/api/people/emile-zola").andExpect {
            jsonPath("$.lifeline[0].text") { value("Born under the July Monarchy") }
            jsonPath("$.lifeline[?(@.text == 'The Third Republic begins')].age") { value(30) }
            jsonPath("$.lifeline[?(@.kind == 'OWN_EVENT' && @.year == 1898)].role") { value("author") }
            jsonPath("$.lifeline[-1].kind") { value("DEATH") }
        }
    }

    @Test
    fun `without a year a person is shown in their prime`() {
        mockMvc.get("/api/people/emile-zola?year=1950").andExpect {
            jsonPath("$.year") { value(1875) }
        }
    }

    @Test
    fun `meanwhile lists people alive elsewhere that year`() {
        mockMvc.get("/api/years/1875/people?exclude=FR").andExpect {
            status { isOk() }
            jsonPath("$[?(@.slug == 'emperor-meiji')]") { exists() }
            jsonPath("$[?(@.regionCode == 'FR')]") { isEmpty() }
        }
    }

    @Test
    fun `parameters outside the spec's constraints are refused`() {
        listOf(
            "/api/years/1875/people?exclude=fr",
            "/api/years/1875/people?exclude=FRA",
            "/api/years/1875/people?exclude=FR&perRegion=0",
            "/api/years/1875/people?exclude=FR&perRegion=6",
            "/api/years/1875/people",
            "/api/years/later/people?exclude=FR",
            "/api/search?q=${"a".repeat(101)}",
        ).forEach { path -> mockMvc.get(path).andExpect { status { isBadRequest() } } }
    }

    @Test
    fun `search ignores accents and finds people and moments`() {
        mockMvc.get("/api/search?q=zola").andExpect {
            jsonPath("$.people[0].slug") { value("emile-zola") }
        }
        mockMvc.get("/api/search?q=kyoto").andExpect {
            jsonPath("$.moments[0].id") { value("kyoto-1590s") }
        }
    }

    @Test
    fun `a person's connections are curated links first, then shared events, in order of year`() {
        mockMvc.get("/api/people/emile-zola").andExpect {
            status { isOk() }
            jsonPath("$.connections[0].person.slug") { value("paul-cezanne") }
            jsonPath("$.connections[0].kind") { value("school friends") }
            jsonPath("$.connections.length()") { value(4) }
            jsonPath("$.connections[3].person.slug") { value("georges-clemenceau") }
            jsonPath("$.connections[3].kind") { value("chose the headline") }
        }
    }

    @Test
    fun `an era only lists people from its own region`() {
        mockMvc.get("/api/eras/jp-meiji-era").andExpect {
            status { isOk() }
            jsonPath("$.people.POWER[?(@.slug == 'emperor-meiji')]") { exists() }
            jsonPath("$.people.ARTS[?(@.slug == 'claude-monet')]") { doesNotExist() }
        }
    }

    @Test
    fun `an era lists the moments set under it`() {
        mockMvc.get("/api/eras/jp-meiji-era").andExpect {
            jsonPath("$.moments.length()") { value(1) }
            jsonPath("$.moments[0].id") { value("tokyo-1870s") }
        }
    }
}
