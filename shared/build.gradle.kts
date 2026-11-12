import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    android {
        namespace = "com.artkachenko.livetextfinder.shared"
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
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    sourceSets {
        named("androidHostTest").dependencies {
            implementation(libs.kotlin.testJunit)
            // Execute pinned SDK result constructors/Android matrices at the OCR adapter boundary.
            implementation(libs.robolectric)
            implementation(libs.roborazzi)
        }
        androidMain.dependencies {
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core)
            implementation(libs.camera.camera2)
            implementation(libs.camera.lifecycle)
            implementation(libs.camera.view)
            implementation(libs.camera.mlkit.vision)
            implementation(libs.mlkit.text.recognition)
            implementation(libs.androidx.exifinterface)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.resources)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

val screenshotBaselines = layout.projectDirectory.dir("src/androidHostTest/screenshots")
val screenshotReports = layout.buildDirectory.dir("reports/screenshot-tests")
val recordScreenshots = providers.gradleProperty("ltfRecordScreenshots").map {
    it.toBooleanStrict()
}.orElse(false)

tasks.withType<Test>().configureEach {
    if (name == "testAndroidHostTest") {
        val recording = recordScreenshots.get()
        val reports = screenshotReports.get().asFile
        inputs.property("recordScreenshots", recording)
        if (!recording) {
            inputs.files(screenshotBaselines.asFileTree)
                .withPathSensitivity(PathSensitivity.RELATIVE)
        }
        outputs.dir(reports)
        outputs.upToDateWhen { !recording }
        outputs.doNotCacheIf("Recording writes source fixtures") { recording }

        systemProperty("ltf.screenshot.record", recording)
        systemProperty("ltf.screenshot.baselines", screenshotBaselines.asFile.absolutePath)
        systemProperty("ltf.screenshot.diffs", reports.resolve("diffs").absolutePath)
        systemProperty("roborazzi.result.dir", reports.resolve("results").absolutePath)
        doFirst {
            reports.deleteRecursively()
        }
    }
}
