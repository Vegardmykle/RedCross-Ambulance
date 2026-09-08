import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
    id("app.cash.sqldelight") version "2.3.2"
    id("co.touchlab.skie") version "0.10.13"
}

sqldelight {
    databases {
        create("AppDatabase") {
            packageName.set("org.example.project.db")
            srcDirs("src/commonMain/data/sqldelight")

            /**
             * Appen er i drift hos mannskapet, så skjemaendringer må skje via
             * migrasjoner – ellers mister enhetene lokale data ved oppdatering.
             *
             * Arbeidsflyt ved en endring:
             *   1. Endre .sq-filene til det nye skjemaet
             *   2. Legg til migrations/<versjon>.sqm med ALTER TABLE-setningene
             *   3. Kjør ./gradlew :sharedLogic:generateAppDatabaseSchema
             *
             * verifyMigrations får bygget til å feile hvis migrasjonene ikke
             * ender opp med nøyaktig samme skjema som .sq-filene beskriver.
             * Det er hele poenget: uten den kan de to skli fra hverandre uten
             * at noen oppdager det før en enhet i en ambulanse krasjer.
             */
            schemaOutputDirectory.set(file("src/commonMain/data/sqldelight/databases"))
            migrationOutputDirectory.set(layout.buildDirectory.dir("migrations"))
            verifyMigrations.set(true)
        }
    }
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "SharedLogic"
            isStatic = true
        }
    }
    
    androidLibrary {
       namespace = "org.example.project.sharedLogic"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_17
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
    }
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.sqldelight.coroutines)
                implementation(libs.gitlive.firestore)
                implementation(libs.gitlive.auth)
            }
        }
        
        val androidMain by getting {
            dependencies {
                implementation(libs.androidDriver)
                implementation(libs.androidx.core.ktx)
                // Firebase Android SDK-versjoner styres av BoM-en (kreves av GitLive)
                implementation(project.dependencies.platform(libs.firebase.bom))
            }
        }

        iosMain.dependencies {
            implementation(libs.nativeDriver)
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        // Enhetstester som kjører på JVM (host) mot en in-memory SQLite-database.
        // Kjøres med: ./gradlew :sharedLogic:testAndroidHostTest
        val androidHostTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.sqldelight.sqliteDriver)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}
