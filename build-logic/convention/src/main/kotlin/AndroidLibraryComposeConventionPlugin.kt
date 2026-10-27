import app.resonance.buildlogic.configureCompose
import app.resonance.buildlogic.libs
import app.resonance.buildlogic.pluginId
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("resonance.android.library")
        pluginManager.apply(libs.pluginId("kotlin-compose"))
        extensions.configure<LibraryExtension> {
            configureCompose(this)
        }
    }
}
