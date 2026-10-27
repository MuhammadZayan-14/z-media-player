import androidx.room.gradle.RoomExtension
import app.resonance.buildlogic.lib
import app.resonance.buildlogic.libs
import app.resonance.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("ksp"))
        pluginManager.apply(libs.pluginId("room"))
        extensions.configure<RoomExtension> {
            // Exported schemas are committed: they are the input for migration tests.
            schemaDirectory("$projectDir/schemas")
        }
        dependencies {
            add("implementation", libs.lib("androidx-room-runtime"))
            add("implementation", libs.lib("androidx-room-ktx"))
            add("ksp", libs.lib("androidx-room-compiler"))
            add("testImplementation", libs.lib("androidx-room-testing"))
            add("androidTestImplementation", libs.lib("androidx-room-testing"))
        }
    }
}
