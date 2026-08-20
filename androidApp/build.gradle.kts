import com.google.firebase.appdistribution.gradle.firebaseAppDistribution
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.googleServices)
    alias(libs.plugins.firebaseAppDistribution)
}

/**
 * Signeringsnøkkel og passord leses fra local.properties, som ikke er i git.
 * Mangler de, bygges release usignert – da feiler distribusjonen med en
 * tydelig melding i stedet for å laste opp noe som ikke kan installeres.
 */
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun localProp(name: String): String? =
    (localProps.getProperty(name) ?: System.getenv(name))?.takeIf { it.isNotBlank() }

val releaseStoreFile = localProp("RELEASE_STORE_FILE")?.let(::file)?.takeIf { it.exists() }

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}
dependencies {
    implementation(projects.sharedUI)

    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "org.example.project"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "no.oslorodekors.ambulanse"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // Øk versionCode for hver utsendelse – ellers ser ikke testerne
        // at det er kommet en ny versjon
        versionCode = 2
        versionName = "1.1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = localProp("RELEASE_STORE_PASSWORD")
                keyAlias = localProp("RELEASE_KEY_ALIAS")
                keyPassword = localProp("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }

            // Beta til mannskapet: ./gradlew :androidApp:betaRelease
            firebaseAppDistribution {
                artifactType = "APK"
                groups = "ambulansegruppa"
                releaseNotesFile = "$rootDir/release-notes.txt"
                // Uten denne brukes innlogget Firebase CLI-bruker (firebase login)
                serviceCredentialsFile = System.getenv("FIREBASE_CREDENTIALS")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

/**
 * Bygger og distribuerer en beta i én kommando:
 *   ./gradlew :androidApp:betaRelease
 * Stopper med en tydelig melding hvis signeringsnøkkelen mangler, slik at
 * det ikke lastes opp en APK testerne ikke får installert.
 */
tasks.register("betaRelease") {
    group = "distribution"
    description = "Bygger signert release-APK og laster den opp til Firebase App Distribution"

    // Lokal kopi, ellers fanger doFirst-lambdaen hele byggeskriptet
    // og konfigurasjonscachen feiler
    val signingKeyPresent = releaseStoreFile != null
    doFirst {
        check(signingKeyPresent) {
            "Fant ikke signeringsnøkkelen. Sett RELEASE_STORE_FILE (og passord) i local.properties."
        }
    }
    dependsOn("assembleRelease", "appDistributionUploadRelease")
}

tasks.matching { it.name == "appDistributionUploadRelease" }
    .configureEach { mustRunAfter("assembleRelease") }