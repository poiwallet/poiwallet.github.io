plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "jp.poiwallet.hogen"
    compileSdk = 35
    defaultConfig {
        applicationId = "jp.poiwallet.hogen"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

// Web版と同じ辞書（リポジトリ直下の dict.json）をアプリに同梱する
val copyDict by tasks.registering(Copy::class) {
    from(rootProject.file("../dict.json"))
    into(layout.projectDirectory.dir("src/main/assets"))
}
tasks.named("preBuild") { dependsOn(copyDict) }
