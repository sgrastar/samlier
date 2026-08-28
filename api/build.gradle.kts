plugins {
    application
}

dependencies {
    implementation(project(":core"))
    implementation(project(":saml"))
    implementation(project(":store"))
    implementation(project(":runner"))
    implementation(project(":peer"))
    implementation(libs.javalin)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.jsr310)
    runtimeOnly(libs.slf4j.simple)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

application {
    mainClass.set("org.samlier.api.SamlierApplication")
}

tasks.processResources {
    dependsOn(":web:buildWeb")
    from(project(":web").layout.buildDirectory.dir("dist")) {
        into("public")
    }
}
