package dev.wholivedwhen.web

import org.springframework.web.bind.annotation.RestController
import dev.wholivedwhen.api.ErasApi
import dev.wholivedwhen.service.EraService

@RestController
class EraController(private val eraService: EraService) : ErasApi {

    override fun listEras() = eraService.list()

    override fun getEra(id: String) = eraService.detail(id)
}
