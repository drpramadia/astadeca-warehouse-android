import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Baca kredensial dari local.properties (tidak di-commit) atau properti -P
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun cfg(key: String, env: String): String =
    (localProps.getProperty(key) ?: System.getenv(env) ?: "").trim()

// Keystore release — file ini tidak di-commit (lihat .gitignore).
val keyProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

val supabaseUrl = cfg("supabase.url", "SUPABASE_URL")
val supabaseAnon = cfg("supabase.anonKey", "SUPABASE_ANON_KEY")
if (supabaseUrl.isEmpty() || supabaseAnon.isEmpty()) {
    // Gagal cepat dengan pesan jelas, bukan APK yang tidak bisa login.
    logger.warn("PERINGATAN: supabase.url / supabase.anonKey belum diisi di local.properties.")
}

android {
    namespace = "com.astadeca.warehouse"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.astadeca.warehouse"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        // Disuntik ke BuildConfig; tidak ditulis ke source tree.
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnon\"")
    }

    signingConfigs {
        if (keyProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = file(keyProps.getProperty("storeFile"))
                storePassword = keyProps.getProperty("storePassword")
                keyAlias = keyProps.getProperty("keyAlias")
                keyPassword = keyProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keyProps.getProperty("storeFile") != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Jaringan — REST Supabase (JSON via org.json bawaan Android)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Penyimpanan token terenkripsi
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Scan barcode/QR
    implementation("com.google.mlkit:barcode-scanning:17.2.0")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
