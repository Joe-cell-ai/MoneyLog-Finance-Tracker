plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val moneylogKeystoreFile = System.getenv("MONEYLOG_KEYSTORE_FILE")
val moneylogStorePassword = System.getenv("MONEYLOG_KEYSTORE_PASSWORD")
val moneylogKeyAlias = System.getenv("MONEYLOG_KEY_ALIAS")
val moneylogKeyPassword = System.getenv("MONEYLOG_KEY_PASSWORD")
val hasMoneylogSigning = listOf(
    moneylogKeystoreFile,
    moneylogStorePassword,
    moneylogKeyAlias,
    moneylogKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "com.openfinanceai"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.openfinanceai"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    if (hasMoneylogSigning) {
        signingConfigs {
            create("moneylogRelease") {
                storeFile = file(moneylogKeystoreFile!!)
                storePassword = moneylogStorePassword
                keyAlias = moneylogKeyAlias
                keyPassword = moneylogKeyPassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            // Use the persistent key on trusted builds; fall back to Android's
            // normal debug key for pull requests where repository secrets are unavailable.
            if (hasMoneylogSigning) {
                signingConfig = signingConfigs.getByName("moneylogRelease")
            }
        }
    }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
