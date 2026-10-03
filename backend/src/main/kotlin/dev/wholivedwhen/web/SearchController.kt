package dev.wholivedwhen.web

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import dev.wholivedwhen.service.SearchService

@RestController
@RequestMapping("/api/search")
class SearchController(private val searchService: SearchService) {

    @GetMapping
    fun search(@RequestParam q: String) = searchService.search(q)
}
