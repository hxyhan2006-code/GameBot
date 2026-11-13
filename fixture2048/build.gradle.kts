plugins { id("com.android.application") }
android {
    namespace = "com.tpcstld.twozerogame"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.example.gamebot2048test"
        minSdk = 29
        targetSdk = 35
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "1.0-offline-test"
    }
    signingConfigs { getByName("debug") {
        storeFile = rootProject.file("keystore/debug.keystore")
        storePassword = "android"; keyAlias = "androiddebugkey"; keyPassword = "android"
    } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:2.0.21"))
}
