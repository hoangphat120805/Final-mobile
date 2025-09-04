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
    repositories {
        google()
        mavenCentral()

        maven {
            url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
            authentication { create<BasicAuthentication>("basic") }
            credentials {
                username = "mapbox"
                password = providers
                    .gradleProperty("MAPBOX_DOWNLOADS_TOKEN")
                    .orNull ?: System.getenv("MAPBOX_DOWNLOADS_TOKEN")
                        ?: throw GradleException("MAPBOX_DOWNLOADS_TOKEN missing")
            }
        }
    }
}


rootProject.name = "VaiChe_Driver"
include(":app")
