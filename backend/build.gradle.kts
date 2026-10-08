// Plugin versions for every project. Each project applies the plugins it needs.
plugins {
	kotlin("jvm") version "2.4.20" apply false
	kotlin("plugin.allopen") version "2.4.20" apply false
	kotlin("plugin.spring") version "2.4.20" apply false
	kotlin("plugin.jpa") version "2.4.20" apply false
	kotlin("kapt") version "2.4.20" apply false
	id("org.springframework.boot") version "4.1.1" apply false
	id("io.spring.dependency-management") version "1.1.7" apply false
}

subprojects {
	group = "dev.wholivedwhen"
	version = "0.0.1-SNAPSHOT"
}
