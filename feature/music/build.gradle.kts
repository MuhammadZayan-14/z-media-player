plugins {
    alias(libs.plugins.resonance.android.feature)
}

android {
    namespace = "app.resonance.feature.music"
}

dependencies {
    implementation(libs.androidx.activity.compose)
}
