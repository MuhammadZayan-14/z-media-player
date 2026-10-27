# AndroidX, Hilt, Room, Media3 and kotlinx.serialization ship their own consumer rules.
# Navigation routes are @Serializable classes looked up by serializer; keep their companions.
-keepclassmembers @kotlinx.serialization.Serializable class app.resonance.** {
    static ** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# Strip verbose logging from release builds (ReleaseTree drops it anyway; this removes the calls).
-assumenosideeffects class timber.log.Timber$Tree {
    public void v(...);
    public void d(...);
}
