plugins {
    alias(libs.plugins.gamedeals.kmp.library)
    alias(libs.plugins.gamedeals.kmp.ksp)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.mokkery)
}

// The sale countdown's fallback dates: `store/site/sales.json` compiled in as a string, so the Worker's file stays the
// only list to edit. Used when the phone has no saved copy and the Worker can't be reached (SaleCalendarRepository).
val generateBundledSales by tasks.registering {
    val salesFile = rootProject.layout.projectDirectory.file("store/site/sales.json")
    val outputDir = layout.buildDirectory.dir("generated/bundledSales/kotlin")
    inputs.file(salesFile)
    outputs.dir(outputDir)
    doLast {
        val literal = salesFile.asFile.readText()
            .replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$").replace("\n", "\\n")
        val target = outputDir.get().file("pm/bam/gamedeals/domain/repositories/salecalendar/BundledSales.kt").asFile
        target.parentFile.mkdirs()
        target.writeText(
            """
            |// Generated from store/site/sales.json by :domain:generateBundledSales. Don't edit.
            |package pm.bam.gamedeals.domain.repositories.salecalendar
            |
            |/** The sale dates built into this app version (`store/site/sales.json` at build time). */
            |internal const val BUNDLED_SALES_JSON: String = "$literal"
            |""".trimMargin(),
        )
    }
}

kotlin {
    sourceSets {
        commonMain {
            kotlin.srcDir(generateBundledSales)
        }

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
