package dev.wholivedwhen

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(properties = ["app.wikipedia.enrich=false"])
class BackendApplicationTests {

	@Test
	fun contextLoads() {
	}

}
