plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// เลขบิลด์ = เลขรอบที่ GitHub รัน (เพิ่มขึ้นทุกครั้ง) → Android ยอมให้ติดตั้งทับเวอร์ชันเก่า
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

// Resolve the test SDK through Gradle so Robolectric does not fetch it from a test worker.
val robolectricSdk by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
}
val robolectricSdkDir = layout.buildDirectory.dir("robolectric-sdk")
val prepareRobolectricSdk by tasks.registering(Sync::class) {
    from(robolectricSdk)
    into(robolectricSdkDir)
}

android {
    namespace = "com.example.universalgamebot"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.universalgamebot"
        minSdk = 29
        targetSdk = 35
        versionCode = buildNumber
        versionName = "0.13.1-basic-auto-clicker"
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
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.dependsOn(prepareRobolectricSdk)
            it.systemProperty("robolectric.offline", "true")
            it.systemProperty("robolectric.dependency.dir", robolectricSdkDir.get().asFile.absolutePath)
        }
    }
}

dependencies {
    robolectricSdk("org.robolectric:android-all-instrumented:15-robolectric-12650502-i7")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
