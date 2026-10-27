plugins {
    alias(libs.plugins.resonance.android.library)
    alias(libs.plugins.resonance.android.hilt)
    alias(libs.plugins.resonance.android.room)
}

android {
    namespace = "app.resonance.core.database"
    // Exported schemas feed MigrationTestHelper in both local (Robolectric) and device tests.
    sourceSets {
        named("test") { assets.directories.add("$projectDir/schemas") }
        named("androidTest") { assets.directories.add("$projectDir/schemas") }
    }
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
}
