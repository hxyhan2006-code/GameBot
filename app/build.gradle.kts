plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// เลขบิลด์ = เลขรอบที่ GitHub รัน (เพิ่มขึ้นทุกครั้ง) → Android ยอมให้ติดตั้งทับเวอร์ชันเก่า
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "com.example.universalgamebot"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.universalgamebot"
        minSdk = 29
        targetSdk = 35
        versionCode = buildNumber
        versionName = "0.5.3"
    }

    // กุญแจเซ็นชุดเดียวทุกครั้ง (keystore/debug.keystore) ห้ามเปลี่ยน/ลบ ไม่งั้นติดตั้งทับไม่ได้
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
