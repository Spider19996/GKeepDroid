import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.chaquo.python")
}

android {
    namespace = "com.gkeepdroid"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.gkeepdroid"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        ndk {
            // -Pabis=arm64-v8a builds only for phones, which makes the APK much smaller.
            abiFilters += ((project.findProperty("abis") as String?) ?: "arm64-v8a,x86_64").split(",")
        }
    }

    // The release key comes from the environment (GitHub Actions) or from local.properties, never from the repository:
    //   keystore.path, keystore.password, key.alias, key.password
    val localProps = rootProject.file("local.properties")
        .takeIf { it.exists() }
        ?.let { file -> Properties().apply { file.inputStream().use { load(it) } } }
    fun secret(env: String, prop: String): String? = System.getenv(env) ?: localProps?.getProperty(prop)
    val keystorePath = secret("KEYSTORE_PATH", "keystore.path")

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = secret("KEYSTORE_PASSWORD", "keystore.password")
                keyAlias = secret("KEY_ALIAS", "key.alias")
                keyPassword = secret("KEY_PASSWORD", "key.password")
            }
        }
    }

    buildTypes {
        release {
            // Without a key the release APK stays unsigned and cannot be installed.
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Optional machine-specific Python 3.13 for the build, set in local.properties:
//   chaquopy.buildPython=/path/to/python3.13
val buildPythonPath: String? = rootProject.file("local.properties")
    .takeIf { it.exists() }
    ?.let { file -> Properties().apply { file.inputStream().use { load(it) } } }
    ?.getProperty("chaquopy.buildPython")

chaquopy {
    defaultConfig {
        version = "3.13"
        buildPythonPath?.let { buildPython(it) }
        pip {
            install("gkeepapi==0.17.1")
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.glance:glance-appwidget:1.2.0")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("androidx.security:security-crypto:1.1.0")
}
