pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven("https://build.shibboleth.net/maven/releases/") {
            content { includeGroupByRegex("org\\.opensaml.*|net\\.shibboleth.*") }
        }
    }
}

rootProject.name = "samlier"

include("core", "saml", "store", "runner", "peer", "api", "web")
