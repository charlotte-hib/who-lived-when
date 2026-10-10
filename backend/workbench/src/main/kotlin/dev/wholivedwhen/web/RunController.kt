package dev.wholivedwhen.web

import org.springframework.web.bind.annotation.RestController
import dev.wholivedwhen.runs.Runs
import dev.wholivedwhen.workbench.api.RunsApi
import dev.wholivedwhen.workbench.api.model.NewRunDto
import dev.wholivedwhen.workbench.api.model.RunDto

@RestController
class RunController(private val runs: Runs) : RunsApi {

    override fun listRuns(): List<RunDto> = runs.all()

    override fun startRun(newRunDto: NewRunDto): RunDto = runs.start(newRunDto.kind, newRunDto.moment)

    override fun getRun(id: Long): RunDto = runs.find(id)
}
