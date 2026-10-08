package dev.wholivedwhen.web

import org.springframework.web.bind.annotation.RestController
import dev.wholivedwhen.api.RegionsApi
import dev.wholivedwhen.service.RegionService

@RestController
class RegionsController(private val regionService: RegionService) : RegionsApi {

    override fun listRegions() = regionService.list()
}
