import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseSigningFile = rootProject.file(".signing/release.properties")
val releaseSigning = Properties().apply {
    if (releaseSigningFile.isFile) {
        releaseSigningFile.inputStream().use { load(it) }
    }
}

val credentialDefaultsFile = rootProject.file(".local/credential-defaults.properties")
val credentialDefaults = Properties().apply {
    if (credentialDefaultsFile.isFile) {
        credentialDefaultsFile.reader(Charsets.UTF_8).use { load(it) }
    }
}

android {
    namespace = "com.automanage.loop"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.automanage.loop"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "1.0.3"
        listOf("DEFAULT_OPENAI_API_KEY", "DEFAULT_INSTAGRAM_USERNAME", "DEFAULT_INSTAGRAM_PASSWORD").forEach { name ->
            val value = credentialDefaults.getProperty(name, "")
                .replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
            buildConfigField("String", name, "\"$value\"")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        create("release") {
            if (releaseSigningFile.isFile) {
                storeFile = rootProject.file(releaseSigning.getProperty("storeFile"))
                storePassword = releaseSigning.getProperty("storePassword")
                keyAlias = releaseSigning.getProperty("keyAlias")
                keyPassword = releaseSigning.getProperty("keyPassword")
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.02.01"))
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("androidx.media3:media3-transformer:1.11.1")
}
