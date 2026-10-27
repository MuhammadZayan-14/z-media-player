import app.resonance.buildlogic.configureAndroidCommon
import app.resonance.buildlogic.libs
import app.resonance.buildlogic.pluginId
import app.resonance.buildlogic.versionInt
import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("android-application"))
        extensions.configure<ApplicationExtension> {
            configureAndroidCommon(this)
            defaultConfig.targetSdk = libs.versionInt("targetSdk")
        }
    }
}
