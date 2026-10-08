// The public API: the Spring Boot app in the production image.
plugins {
	kotlin("jvm")
	kotlin("plugin.spring")
	kotlin("kapt")
	id("org.springframework.boot")
	id("io.spring.dependency-management")
}

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

val mapstructVersion = "1.6.3"

dependencies {
	implementation(project(":core"))
	implementation("org.springframework.boot:spring-boot-h2console")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-restclient")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("tools.jackson.module:jackson-module-kotlin")
	implementation("org.mapstruct:mapstruct:$mapstructVersion")
	implementation("com.anthropic:anthropic-java:2.68.0")
	kapt("org.mapstruct:mapstruct-processor:$mapstructVersion")
	runtimeOnly("com.h2database:h2")
	runtimeOnly("io.micrometer:micrometer-registry-prometheus")
	testImplementation("org.springframework.boot:spring-boot-starter-restclient-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
}

kapt {
	arguments {
		arg("mapstruct.defaultComponentModel", "spring")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}

// The release the tests and bootRun load: the repository's sample/. The Docker image has its own copy.
val sample = rootProject.file("../sample")

tasks.test {
	inputs.dir(sample).withPropertyName("sample").withPathSensitivity(PathSensitivity.RELATIVE)
	systemProperty("app.release.dir", sample.path)
}

// Run from backend/, as before the split, so story drafts still land in backend/drafts.
tasks.bootRun {
	workingDir = rootProject.projectDir
	systemProperty("app.release.dir", sample.path)
}
