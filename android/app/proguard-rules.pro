# Supabase / Ktor
-keep class io.github.jan.supabase.** { *; }
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class com.calltranscriber.**$$serializer { *; }
-keepclassmembers class com.calltranscriber.** { *** Companion; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
