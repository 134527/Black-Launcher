plugins {
    id("com.android.application")
}

android {
    namespace = "com.msk.blacklauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.msk.blacklauncher"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}



dependencies {


    implementation(libs.material)
    implementation(libs.activity)
    // 降级 constraintlayout 版本
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // 降级 appcompat 版本
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation(libs.gridlayout)
    implementation ("com.google.code.gson:gson:2.8.7")

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

}