plugins { id("com.android.application") }
android {
    namespace = "com.faddensoft.breakout"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.example.gamebotbricktest"
        minSdk = 29
        targetSdk = 35
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "1.0-brick-readonly-test"
    }
    signingConfigs { getByName("debug") {
        storeFile = rootProject.file("keystore/debug.keystore")
        storePassword = "android"; keyAlias = "androiddebugkey"; keyPassword = "android"
    } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
