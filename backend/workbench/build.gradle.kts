// The workbench: the curator's tools, on the Mac only, never in an image. Today: drafting a moment's story with Claude.
plugins {
	kotlin("jvm")
	kotlin("plugin.spring")
	id("org.springframework.boot")
	id("io.spring.dependency-management")
}

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

dependencies {
	implementation(project(":core"))
	implementation("org.springframework.boot:spring-boot-starter-jackson")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("tools.jackson.module:jackson-module-kotlin")
	implementation("com.anthropic:anthropic-java:2.68.0")
	implementation("org.springframework.boot:spring-boot-starter-restclient")
	// One shared pace for every call to Wikimedia: rate limiter, bulkhead and retry, configured in application.yaml.
	implementation("io.github.resilience4j:resilience4j-spring-boot4:2.4.0")
	// Postgres on ./gradlew :workbench:bootRun: starts the db service of compose.workbench.yaml and connects to it.
	developmentOnly("org.springframework.boot:spring-boot-docker-compose")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testImplementation(testFixtures(project(":core")))
	// A fake Wikimedia on a real port, answering with responses recorded from the real one.
	testImplementation("org.wiremock.integrations:wiremock-spring-boot:4.4.3")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}

// The release the workbench loads into its database: the repository's sample/.
val sample = rootProject.file("../sample")
// The tests' Postgres runs the image of the db service in the repository's Compose file (core's PostgresTestConfiguration).
val compose = rootProject.file("../docker-compose.yml")

tasks.test {
	inputs.dir(sample).withPropertyName("sample").withPathSensitivity(PathSensitivity.RELATIVE)
	inputs.file(compose).withPropertyName("compose").withPathSensitivity(PathSensitivity.RELATIVE)
	systemProperty("app.release.dir", sample.path)
	systemProperty("compose.file", compose.path)
}

// Run from backend/, so story drafts land in backend/drafts as before. Its Postgres is compose.workbench.yaml, at the
// repository's root, not the site's backend/compose.yaml.
tasks.bootRun {
	workingDir = rootProject.projectDir
	systemProperty("app.release.dir", sample.path)
	systemProperty("spring.docker.compose.file", rootProject.file("../compose.workbench.yaml").path)
}
