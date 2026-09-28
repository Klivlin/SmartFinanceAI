# SmartFinance AI — ProGuard / R8 rules

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class ru.vogu.smartfinance.**$$serializer { *; }
-keepclassmembers class ru.vogu.smartfinance.** {
    *** Companion;
}
-keepclasseswithmembers class ru.vogu.smartfinance.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# SQLCipher
-keep class net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**

# Retrofit / OkHttp
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keepattributes Signature, Exceptions

# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Data / Domain модели — сериализуются рефлексивно, не должны быть obfuscated
-keep class ru.vogu.smartfinance.core.database.entity.** { *; }
-keep class ru.vogu.smartfinance.domain.model.** { *; }
-keep class ru.vogu.smartfinance.core.network.dto.** { *; }
