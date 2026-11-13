import java.net.URI
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

val screenshotIndex = layout.buildDirectory.file("reports/screenshot-index/index.html")

tasks.register("generateScreenshotIndex") {
    group = "verification"
    description = "Generates an HTML index of the screenshot baselines by tier."
    val fixtures = screenshotBaselines.asFile
    val index = screenshotIndex.get().asFile
    inputs.dir(fixtures).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.file(index)

    doLast {
        val reportDirectory = checkNotNull(index.parentFile)
        reportDirectory.mkdirs()
        index.bufferedWriter().use { html ->
            html.appendLine(
                """
                <!doctype html>
                <html lang="en">
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Live Text Finder screenshot baselines</title>
                <style>
                  body { font: 1rem system-ui; margin: 2rem; }
                  section { margin-block: 2rem; }
                  .images {
                    display: grid;
                    grid-template-columns: repeat(auto-fill, minmax(min(100%, 18rem), 1fr));
                    gap: 1rem;
                  }
                  figure { margin: 0; padding: .75rem; border: 1px solid #888; }
                  img { display: block; max-width: 100%; height: auto; margin-inline: auto; }
                  figcaption { margin-top: .75rem; overflow-wrap: anywhere; }
                </style>
                <h1>Live Text Finder screenshot baselines</h1>
                <nav><a href="#element">Element</a> · <a href="#region">Region</a> ·
                  <a href="#screen">Screen</a></nav>
                """.trimIndent(),
            )
            for (tier in listOf("element", "region", "screen")) {
                val pngs = fixtures.resolve(tier).listFiles().orEmpty()
                    .filter { it.isFile && it.extension == "png" }
                    .sortedBy { it.name }
                val heading = tier.replaceFirstChar { it.uppercase() }
                html.appendLine("""<section id="$tier"><h2>$heading (${pngs.size})</h2>""")
                html.appendLine("""<div class="images">""")
                for (png in pngs) {
                    val relative = reportDirectory.toPath().relativize(png.toPath())
                        .joinToString("/")
                    val href = URI(null, null, relative, null).toASCIIString().replace("&", "&amp;")
                    val name = png.name
                        .replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                        .replace("\"", "&quot;")
                    html.appendLine("""<figure><a href="$href">""")
                    html.appendLine("""<img src="$href" alt="$name" loading="lazy"></a>""")
                    html.appendLine("<figcaption><code>$name</code></figcaption></figure>")
                }
                html.appendLine("</div></section>")
            }
            html.appendLine("</html>")
        }
    }
}
