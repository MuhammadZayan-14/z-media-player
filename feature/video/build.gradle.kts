plugins {
    alias(libs.plugins.resonance.android.feature)
}

android {
    namespace = "app.resonance.feature.video"
}

dependencies {
    implementation(projects.core.media)
    implementation(projects.core.common)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.ui.compose)

    testImplementation(libs.androidx.media3.test.utils)
    testImplementation(libs.androidx.test.ext.junit)
}
