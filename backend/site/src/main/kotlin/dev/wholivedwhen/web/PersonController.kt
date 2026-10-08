package dev.wholivedwhen.web

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import dev.wholivedwhen.service.PersonService

@RestController
@RequestMapping("/api/people")
class PersonController(private val personService: PersonService) {

    /** [year] picks the moment to look at them in, e.g. from a moment page; their prime when absent. */
    @GetMapping("/{slug}")
    fun detail(@PathVariable slug: String, @RequestParam(required = false) year: Int?) = personService.detail(slug, year)
}
