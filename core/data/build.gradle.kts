plugins {
    alias(libs.plugins.resonance.android.library)
    alias(libs.plugins.resonance.android.hilt)
}

android {
    namespace = "app.resonance.core.data"
}

dependencies {
    api(projects.core.domain)
    implementation(projects.core.common)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.permissions)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.room.runtime)
}
