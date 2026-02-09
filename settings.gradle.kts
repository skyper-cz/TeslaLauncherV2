pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        maven {
            url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
            authentication {
                create<BasicAuthentication>("basic")
            }
            credentials {
                username = "mapbox"
                // Read secret token from local.properties
                val localProperties = java.util.Properties()
                val localFile = File(settings.rootDir, "local.properties")
                if (localFile.exists()) {
                    localFile.inputStream().use { localProperties.load(it) }
                }
                password = localProperties.getProperty("MAPBOX_DOWNLOADS_TOKEN")
            }
        }
    }
}

rootProject.name = "TeslaLauncherV2"
include(":app")
