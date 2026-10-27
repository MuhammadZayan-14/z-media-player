import app.resonance.buildlogic.configureAndroidCommon
import app.resonance.buildlogic.lib
import app.resonance.buildlogic.libs
import app.resonance.buildlogic.pluginId
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("android-library"))
        extensions.configure<LibraryExtension> {
            configureAndroidCommon(this)
            defaultConfig.consumerProguardFiles("consumer-rules.pro")
            // Robolectric unit tests run against a fixed, released platform.
            testOptions.unitTests.all {
                it.systemProperty("robolectric.logging.enabled", "false")
                // Gradle 9 fails test tasks that find no tests; modules without tests are expected.
                it.failOnNoDiscoveredTests.set(false)
            }
        }
        dependencies {
            // Shared fakes, rules and robolectric.properties for every module's local tests.
            if (path != ":core:testing") add("testImplementation", project(":core:testing"))
            add("testImplementation", libs.lib("junit4"))
            add("testImplementation", libs.lib("kotlinx-coroutines-test"))
            add("testImplementation", libs.lib("turbine"))
            add("testImplementation", libs.lib("mockk"))
        }
    }
}
