pluginManagement {
    repositories {
        maven {
            url = uri("https://maven.google.com")
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven {
            url = uri("https://maven.google.com")
        }
        mavenCentral()
    }
}

rootProject.name = "android_ulink_sdk"
include(":ulink-sdk")
project(":ulink-sdk").projectDir = file("app")
 