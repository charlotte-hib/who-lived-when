import org.springframework.boot.gradle.plugin.SpringBootPlugin

// The model every app shares: JPA entities, repositories and small helpers. A library, not an app.
plugins {
	`java-library`
	kotlin("jvm")
	kotlin("plugin.allopen")
	kotlin("plugin.jpa")
}

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

dependencies {
	// Gradle's own BOM support, not the dependency-management plugin: without the Spring Boot plugin to
	// align it, that plugin would pin the Kotlin compiler to the BOM's older Kotlin.
	implementation(platform(SpringBootPlugin.BOM_COORDINATES))
	// Entities and repositories are part of core's API: every app that uses them needs JPA.
	api("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("tools.jackson.module:jackson-module-kotlin")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
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

// SampleReleaseTests compile the repository's sample/ release.
val sample = rootProject.file("../sample")

tasks.test {
	inputs.dir(sample).withPropertyName("sample").withPathSensitivity(PathSensitivity.RELATIVE)
	systemProperty("sample.dir", sample.path)
}
