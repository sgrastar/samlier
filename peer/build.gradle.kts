plugins { `java-library` }

dependencies {
    api(project(":core"))
    implementation(project(":runner"))
    implementation(project(":saml"))
    implementation(project(":store"))
    implementation(libs.jackson.databind)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}
