plugins {
    alias(libs.plugins.gamedeals.kmp.library)
    alias(libs.plugins.gamedeals.kmp.ksp)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.mokkery)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx)
            api(libs.kotlinx.collections.immutable)
            api(libs.kotlinx.datetime)

            implementation(libs.coroutines)
            implementation(libs.compose.runtime)
            implementation(libs.room.runtime.multiplatform)
            implementation(libs.koin.core)

            implementation(project(":common"))
            implementation(project(":logging"))
        }

        androidMain.dependencies {
            implementation(libs.koin.android)

            implementation(libs.room.runtime)
            // Background notification polling (Phase B): WorkManager periodic worker + scheduler.
            implementation(libs.androidx.work.runtime.ktx)
        }

        iosMain.dependencies {
            implementation(libs.androidx.sqlite.bundled)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.testing)
            implementation(project(":testing"))
        }

        iosTest.dependencies {
            implementation(libs.room.testing)
        }

        val androidHostTest by getting {
            dependencies {
                implementation(project(":testing"))
                implementation(libs.junit)
                implementation(libs.mockk)
                implementation(libs.coroutines.testing)
                implementation(libs.core.testing)
                implementation(libs.kotlinx)
            }
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
}

// The simulator only forwards SIMCTL_CHILD_-prefixed variables to the test process; the migration test reads the schemas from here.
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest>().configureEach {
    environment("SIMCTL_CHILD_ROOM_SCHEMA_DIR", "$projectDir/schemas")
}
