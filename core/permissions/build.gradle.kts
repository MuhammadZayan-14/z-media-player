plugins {
    alias(libs.plugins.resonance.android.library)
    alias(libs.plugins.resonance.android.hilt)
}

android {
    namespace = "app.resonance.core.permissions"
}

dependencies {
    implementation(libs.androidx.core.ktx)
}
