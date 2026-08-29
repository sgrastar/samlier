plugins { `java-library` }

dependencies {
    api(project(":core"))
    implementation(libs.opensaml.core.api)
    implementation(libs.opensaml.core.impl)
    implementation(libs.opensaml.saml.api)
    implementation(libs.opensaml.saml.impl)
    implementation(libs.xmlsec)
    implementation(libs.bcprov)
    implementation(libs.bcpkix)
    // OpenSAML 5.2.3 currently requests older transitive releases with known high-severity issues.
    // Keep the fixed, binary-compatible lines explicit until OpenSAML carries them itself.
    implementation(libs.commons.lang3)
    implementation(libs.httpcore5)
    implementation(libs.httpcore5.h2)
    implementation(libs.jackson.databind)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}
