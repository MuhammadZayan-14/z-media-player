import app.resonance.buildlogic.configureJvmTarget
import app.resonance.buildlogic.lib
import app.resonance.buildlogic.libs
import app.resonance.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Pure Kotlin module with no Android framework dependency. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("kotlin-jvm"))
        configureJvmTarget()
        dependencies {
            add("testImplementation", libs.lib("junit4"))
            add("testImplementation", libs.lib("kotlinx-coroutines-test"))
            add("testImplementation", libs.lib("turbine"))
            add("testImplementation", libs.lib("mockk"))
        }
    }
}
