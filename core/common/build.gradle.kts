plugins {
    alias(libs.plugins.resonance.android.library)
    alias(libs.plugins.resonance.android.hilt)
}

android {
    namespace = "app.resonance.core.common"
}

dependencies {
    api(libs.kotlinx.coroutines.android)
    api(libs.timber)
}
