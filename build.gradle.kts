import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.Exec
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    base
}

group = "com.samlscope"
version = "0.1.0"

subprojects {
    group = rootProject.group
    version = rootProject.version

    pluginManager.withPlugin("java") {
        extensions.configure<JavaPluginExtension> {
            toolchain.languageVersion.set(JavaLanguageVersion.of(21))
            withSourcesJar()
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
            testLogging {
                events("failed", "skipped")
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            }
        }
    }
}

tasks.named("check") {
    dependsOn(
        ":core:check",
        ":saml:check",
        ":store:check",
        ":runner:check",
        ":peer:check",
        ":api:check",
        ":web:check",
    )
}

val releasePolicyCheck = tasks.register<Exec>("releasePolicyCheck") {
    description = "Verifies signed G1/G2 approvals and all release policy artifacts."
    group = "verification"
    workingDir(rootDir)
    val python = providers.environmentVariable("PY")
        .orElse(layout.projectDirectory.file(".venv/bin/python").asFile.absolutePath)
    commandLine(python.get(), "tools/release_check.py")
    outputs.file(layout.buildDirectory.file("release-check-report.json"))
    outputs.upToDateWhen { false }
    mustRunAfter(tasks.named("check"))
}

val releasePolicyUnitTest = tasks.register<Exec>("releasePolicyUnitTest") {
    description = "Runs unit tests for the fail-closed release policy checker."
    group = "verification"
    workingDir(rootDir)
    val python = providers.environmentVariable("PY").orElse("python3")
    commandLine(
        python.get(), "-m", "unittest",
        "tools/tests/test_release_check.py", "dev/keycloak/test_smoke.py",
        "dev/keycloak/test_prepare_smoke_apple.py",
    )
}

tasks.named("check") {
    dependsOn(releasePolicyUnitTest)
}

tasks.register("releaseCheck") {
    description = "Runs the complete fail-closed verification required before a release or container publication."
    group = "verification"
    dependsOn(tasks.named("check"), releasePolicyCheck)
}
