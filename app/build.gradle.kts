plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "com.roshan.transfer"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.roshan.transfer"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
}
