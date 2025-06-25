plugins {
    id("com.android.application")
}

android {

    signingConfigs {
        getByName("debug") {
            storeFile = file("E:\\JryO\\MT8396\\mtk_platform.jks")
            keyAlias = "platform"
            storePassword = "123456"
            keyPassword = "123456"
        }
    }
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


// 在 build.gradle.kts 中
configurations.all {
    resolutionStrategy {
        force("androidx.lifecycle:lifecycle-livedata-core:2.6.2")
        force("androidx.lifecycle:lifecycle-runtime:2.6.2")
        force("androidx.lifecycle:lifecycle-viewmodel:2.6.2")
        // 其他 lifecycle 相关库
    }
}
dependencies {


    implementation(libs.material)
    implementation(libs.activity)
    // 降级 constraintlayout 版本
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.core:core:1.12.0")
    implementation("androidx.window:window:1.2.0")
    implementation("androidx.core:core-ktx:1.12.0")
    // 降级 appcompat 版本
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation(libs.gridlayout)
    implementation ("com.google.code.gson:gson:2.8.7")
    implementation ("com.github.centerzx:ShapeBlurView:1.0.5")
    implementation ("com.github.Dimezis:BlurView:version-2.0.3")
    implementation("sh.calvin.reorderable:reorderable:2.4.3")
    implementation ("com.github.li-xiaojun:XPopup:2.10.0")
    implementation ("com.google.android.material:material:1.4.0")
    implementation ("androidx.recyclerview:recyclerview:1.2.1")
    compileOnly(files("E:\\JryO\\Black-Launcher\\app\\libs\\framework.jar"))


    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

}