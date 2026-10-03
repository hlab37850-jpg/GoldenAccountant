plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// بيانات التوقيع تأتي من متغيرات البيئة (أسرار GitHub) ولا تُكتب في الكود أبداً
val keystorePath: String? = System.getenv("KEYSTORE_FILE")
val hasSigning = keystorePath != null && File(keystorePath).exists()

android {
    namespace = "com.golden.accountant"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.golden.accountant"
        minSdk = 24
        targetSdk = 36          // متطلب Google Play للتطبيقات الجديدة منذ 31-8-2026
        versionCode = 9
        versionName = "1.2.0"
    }

    signingConfigs {
        if (hasSigning) create("release") {
            storeFile = File(keystorePath!!)
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            // التصغير (R8) متوقف عمداً حتى تُجرَّب النسخة على أجهزة حقيقية؛ فعّله بعد الاختبار لتقليل الحجم
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasSigning) signingConfig = signingConfigs.getByName("release")
        }
    }
    lint {
        // لا يمنع فحص lint بناء النسخة النهائية؛ راجع تقريره يدوياً قبل النشر
        abortOnError = false
        checkReleaseBuilds = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    val room = "2.7.1"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    implementation(platform("androidx.compose:compose-bom:2025.05.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.navigation:navigation-compose:2.8.9")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
}
