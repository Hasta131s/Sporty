# Proguard rules for Flofys application

# Keep Room generated files
-keep class * extends androidx.room.RoomDatabase
-keep class com.example.data.database.** { *; }
-dontwarn androidx.room.**
