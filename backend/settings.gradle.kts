rootProject.name = "backend"

// core: the model every app shares (entities, repositories, helpers).
// site: the public API, and the only project in the production image.
include("core", "site")

dependencyResolutionManagement {
	repositories {
		mavenCentral()
	}
}
