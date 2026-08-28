import org.gradle.api.tasks.Exec

plugins { base }

val npmInstall = tasks.register<Exec>("npmInstall") {
    inputs.files("package.json", "package-lock.json")
    outputs.dir("node_modules")
    commandLine("npm", "ci", "--ignore-scripts")
}

val buildWeb = tasks.register<Exec>("buildWeb") {
    dependsOn(npmInstall)
    inputs.dir("src")
    inputs.files("index.html", "package.json", "package-lock.json", "tsconfig.json", "vite.config.ts")
    outputs.dir(layout.buildDirectory.dir("dist"))
    commandLine("npm", "run", "build", "--", "--outDir", layout.buildDirectory.dir("dist").get().asFile.absolutePath)
}

val testWeb = tasks.register<Exec>("testWeb") {
    dependsOn(npmInstall)
    inputs.dir("src")
    inputs.files("package.json", "package-lock.json", "tsconfig.json", "vite.config.ts")
    commandLine("npm", "test", "--", "--run")
}

tasks.named("assemble") { dependsOn(buildWeb) }
tasks.named("check") { dependsOn(testWeb, buildWeb) }
