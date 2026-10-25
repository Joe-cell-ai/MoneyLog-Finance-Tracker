plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val moneyLogKeystorePath = System.getenv("MONEYLOG_KEYSTORE_PATH")
val moneyLogKeystorePassword = System.getenv("MONEYLOG_KEYSTORE_PASSWORD")
val moneyLogKeyAlias = System.getenv("MONEYLOG_KEY_ALIAS")
val moneyLogKeyPassword = System.getenv("MONEYLOG_KEY_PASSWORD")
val hasMoneyLogSigning = !moneyLogKeystorePath.isNullOrBlank() &&
    !moneyLogKeystorePassword.isNullOrBlank() &&
    !moneyLogKeyAlias.isNullOrBlank() &&
    !moneyLogKeyPassword.isNullOrBlank()

android {
    namespace = "com.openfinanceai"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.openfinanceai"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"
    }
    signingConfigs {
        if (hasMoneyLogSigning) {
            create("moneyLogRelease") {
                storeFile = file(moneyLogKeystorePath!!)
                storePassword = moneyLogKeystorePassword
                keyAlias = moneyLogKeyAlias
                keyPassword = moneyLogKeyPassword
            }
        }
    }
    buildTypes {
        release {
            if (hasMoneyLogSigning) {
                signingConfig = signingConfigs.getByName("moneyLogRelease")
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
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
