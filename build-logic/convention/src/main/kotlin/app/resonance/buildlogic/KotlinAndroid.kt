package app.resonance.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.jlleitschuh.gradle.ktlint.KtlintExtension

private val JAVA_VERSION = JavaVersion.VERSION_17

/** SDK levels, Java level, lint and unit-test defaults shared by every Android module. */
internal fun Project.configureAndroidCommon(extension: CommonExtension) {
    extension.apply {
        compileSdk = libs.versionInt("compileSdk")
        defaultConfig.minSdk = libs.versionInt("minSdk")
        defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        compileOptions.sourceCompatibility = JAVA_VERSION
        compileOptions.targetCompatibility = JAVA_VERSION

        testOptions.unitTests.isIncludeAndroidResources = true
        testOptions.unitTests.isReturnDefaultValues = false

        lint.abortOnError = true
        lint.warningsAsErrors = false
        lint.checkDependencies = false
        // Version-catalog bumps are a deliberate, reviewed change, not a lint failure.
        lint.disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
    configureKotlinCompiler()
    configureKtlint()
}

/** Kotlin compiler strictness shared by Android and JVM modules. */
internal fun Project.configureKotlinCompiler() {
    val warningsAsErrors = providers.gradleProperty("resonance.warningsAsErrors")
        .map(String::toBoolean)
        .getOrElse(true)
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            allWarningsAsErrors.set(warningsAsErrors)
            freeCompilerArgs.addAll(
                "-Xjsr305=strict",
                "-Xconsistent-data-class-copy-visibility",
            )
        }
    }
}

internal fun Project.configureJvmTarget() {
    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JAVA_VERSION
        targetCompatibility = JAVA_VERSION
    }
    configureKotlinCompiler()
    configureKtlint()
}

internal fun Project.configureKtlint() {
    pluginManager.apply(libs.pluginId("ktlint"))
    extensions.configure<KtlintExtension> {
        version.set(libs.findVersion("ktlint").get().requiredVersion)
        android.set(true)
        filter {
            exclude { it.file.path.contains("${java.io.File.separator}build${java.io.File.separator}") }
        }
    }
}
