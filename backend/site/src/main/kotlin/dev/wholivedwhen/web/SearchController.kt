package dev.wholivedwhen.web

import org.springframework.web.bind.annotation.RestController
import dev.wholivedwhen.api.SearchApi
import dev.wholivedwhen.service.SearchService

@RestController
class SearchController(private val searchService: SearchService) : SearchApi {

    override fun search(q: String) = searchService.search(q)
}
