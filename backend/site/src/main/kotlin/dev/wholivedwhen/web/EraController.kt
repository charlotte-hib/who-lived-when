package dev.wholivedwhen.web

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import dev.wholivedwhen.service.EraService

@RestController
@RequestMapping("/api/eras")
class EraController(private val eraService: EraService) {

    @GetMapping
    fun list() = eraService.list()

    @GetMapping("/{id}")
    fun detail(@PathVariable id: String) = eraService.detail(id)
}
