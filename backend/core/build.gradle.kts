import org.springframework.boot.gradle.plugin.SpringBootPlugin

// The model every app shares: JPA entities, repositories and small helpers. A library, not an app.
plugins {
	`java-library`
	kotlin("jvm")
	kotlin("plugin.allopen")
	kotlin("plugin.jpa")
	kotlin("kapt")
}

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

val mapstructVersion = "1.6.3"

dependencies {
	// Gradle's own BOM support, not the dependency-management plugin: without the Spring Boot plugin to
	// align it, that plugin would pin the Kotlin compiler to the BOM's older Kotlin.
	implementation(platform(SpringBootPlugin.BOM_COORDINATES))
	// Entities and repositories are part of core's API: every app that uses them needs JPA.
	api("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("tools.jackson.module:jackson-module-kotlin")
	implementation("org.mapstruct:mapstruct:$mapstructVersion")
	kapt("org.mapstruct:mapstruct-processor:$mapstructVersion")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testImplementation("com.github.victools:jsonschema-generator:5.0.0")
	testImplementation("com.networknt:json-schema-validator:3.0.8")
	testRuntimeOnly("com.h2database:h2")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

// The tests compile the repository's sample/ release and check it against its JSON Schema.
val sample = rootProject.file("../sample")

tasks.test {
	inputs.dir(sample).withPropertyName("sample").withPathSensitivity(PathSensitivity.RELATIVE)
	systemProperty("sample.dir", sample.path)
}

tasks.register<JavaExec>("releaseSchema") {
	description = "Writes the release format's JSON Schema, generated from its records, to sample/release.schema.json."
	classpath = sourceSets.test.get().runtimeClasspath
	mainClass = "dev.wholivedwhen.release.ReleaseSchemaKt"
	args(sample.resolve("release.schema.json").path)
}
