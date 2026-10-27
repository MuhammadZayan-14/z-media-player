plugins {
    alias(libs.plugins.resonance.android.library)
}

android {
    namespace = "app.resonance.core.testing"
}

dependencies {
    api(projects.core.domain)
    api(libs.junit4)
    api(libs.kotlinx.coroutines.test)
    api(libs.turbine)
    api(libs.androidx.test.runner)
    api(libs.hilt.android.testing)
}
