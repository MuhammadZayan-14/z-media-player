plugins {
    alias(libs.plugins.resonance.android.library)
    alias(libs.plugins.resonance.android.hilt)
}

android {
    namespace = "app.resonance.core.datastore"
}

dependencies {
    api(projects.core.model)
    api(libs.androidx.datastore.preferences)
    implementation(projects.core.common)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
}
