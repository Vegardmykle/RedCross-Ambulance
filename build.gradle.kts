plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
}

/**
 * Legger byggeutdata utenfor prosjektmappa når `buildDirRoot` er satt i
 * local.properties (eller som Gradle-egenskap). Nødvendig hvis prosjektet
 * ligger i en mappe som synkroniseres av iCloud/OneDrive: synkroniseringen
 * lager kopier med « 2» i navnet mens Gradle skriver, og D8 feiler med
 * «defined multiple times».
 *
 * Stien er maskinspesifikk og hører derfor ikke hjemme i gradle.properties,
 * som er i git. Er egenskapen ikke satt, brukes standard build/-mappe.
 */
val buildDirRoot = providers.gradleProperty("buildDirRoot").orNull
    ?: rootProject.file("local.properties").takeIf { it.exists() }?.let { file ->
        java.util.Properties().apply { file.inputStream().use { load(it) } }.getProperty("buildDirRoot")
    }

if (!buildDirRoot.isNullOrBlank()) {
    val root = file(buildDirRoot)
    rootProject.layout.buildDirectory.set(root.resolve(rootProject.name))
    subprojects {
        layout.buildDirectory.set(root.resolve("${rootProject.name}/${project.name}"))
    }
}