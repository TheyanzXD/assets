# "The Lost Echo" ProGuard / R8 rules (release build).
# The game draws everything procedurally; keep the public entry point.
-keep class com.thelostecho.game.MainActivity { *; }

# Keep all game classes intact (reflection-free, but cheap insurance).
-keep class com.thelostecho.game.** { *; }

# Keep Parcelable/SaveData classes if any get serialized by name.
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# Optimize aggressively, no debug info in release.
-optimizationpasses 5
-dontpreverify
-verbose
