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
           jvmTarget = JvmTarget.JVM_11
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
    }
}
