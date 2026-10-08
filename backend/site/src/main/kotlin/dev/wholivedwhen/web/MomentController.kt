package dev.wholivedwhen.web

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import dev.wholivedwhen.service.MomentService

@RestController
@RequestMapping("/api/moments")
class MomentController(private val momentService: MomentService) {

    @GetMapping
    fun list() = momentService.list()

    @GetMapping("/{id}")
    fun detail(@PathVariable id: String) = momentService.detail(id)

    @GetMapping("/{id}/story")
    fun story(@PathVariable id: String) = momentService.story(id)
}
