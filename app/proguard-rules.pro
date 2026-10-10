# --- OkHttp ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- org.json (stdlib Android; jangan di-strip) ---
-keep class org.json.** { *; }

# --- ML Kit barcode ---
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# --- Kotlin metadata & lambdas (Compose mengandalkan ini) ---
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations

# Compose navigation rute string tetap aman, tapi simpan nama enum/objek refleksi.
-keepclassmembers enum * { *; }
