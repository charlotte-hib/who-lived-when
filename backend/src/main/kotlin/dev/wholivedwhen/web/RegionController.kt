package dev.wholivedwhen.web

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import dev.wholivedwhen.service.PersonService
import dev.wholivedwhen.service.RegionService

@RestController
@RequestMapping("/api")
class RegionController(private val regionService: RegionService, private val personService: PersonService) {

    @GetMapping("/regions")
    fun list() = regionService.list()

    /** A few people alive in [year] in each region except [exclude]: the "meanwhile, elsewhere" of a moment. */
    @GetMapping("/years/{year}/people")
    fun aliveElsewhere(
        @PathVariable year: Int,
        @RequestParam exclude: String,
        @RequestParam(defaultValue = "2") perRegion: Int,
    ) = personService.aliveElsewhere(exclude.uppercase(), year, perRegion.coerceIn(1, 5))
}
