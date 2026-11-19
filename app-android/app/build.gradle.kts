import java.util.Properties

plugins {
    id("com.android.application")
}

android {
    namespace = "ar.pariz.guarani"
    compileSdk = 37

    defaultConfig {
        applicationId = "ar.pariz.guarani"
        minSdk = 26
        targetSdk = 36
        versionCode = 11
        versionName = "1.6"
    }

    // La clave de firma NO está en el repositorio. Para firmar, poné guarani.keystore y
    // keystore.properties (storePassword, keyAlias, keyPassword) en esta carpeta app/.
    val firma = Properties().apply {
        val f = file("keystore.properties"); if (f.exists()) f.inputStream().use { load(it) }
    }
    signingConfigs {
        if (!firma.isEmpty) create("release") {
            storeFile = file("guarani.keystore")
            storePassword = firma.getProperty("storePassword")
            keyAlias = firma.getProperty("keyAlias")
            keyPassword = firma.getProperty("keyPassword")
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests {
        isIncludeAndroidResources = true
        all { t -> System.getProperty("capturas")?.let { t.systemProperty("capturas", it) } }
    } }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("androidx.security:security-crypto:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.18.1")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("androidx.work:work-testing:2.12.0")
    testImplementation("org.json:json:20260814")
}
