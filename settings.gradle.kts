// ANI-KUTA root settings
// 5 modules: app, core, data, domain, source-api

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ANI-KUTA"

include(":app")
include(":core")
include(":data")
include(":domain")
include(":source-api")
