import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.dagger.hilt.android")
}

// 민감 설정: PetManager/keystore.properties (Git 제외) — keystore.properties.example 참고
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
} else {
    logger.warn(
        "keystore.properties 가 없습니다. keystore.properties.example 을 복사해 " +
            "kakao_native_app_key, SERVER_BASE_URL 을 설정하세요.",
    )
}

fun Properties.secretOrEmpty(key: String): String =
    getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }.orEmpty()

fun normalizeBaseUrl(raw: String): String {
    if (raw.isEmpty()) return ""
    return if (raw.endsWith("/")) raw else "$raw/"
}

val kakaoNativeAppKey = keystoreProperties.secretOrEmpty("kakao_native_app_key")
val serverBaseUrl = normalizeBaseUrl(keystoreProperties.secretOrEmpty("SERVER_BASE_URL"))

android {
    namespace = "com.petmanager"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.project.petmanager"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", "\"$kakaoNativeAppKey\"")
        manifestPlaceholders["KAKAO_NATIVE_APP_KEY"] = kakaoNativeAppKey
        buildConfigField("String", "SERVER_BASE_URL", "\"$serverBaseUrl\"")
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    
    kapt {
        correctErrorTypes = true
        useBuildCache = false
    }
    buildFeatures{
        buildConfig = true
        viewBinding = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }

}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Jetpack Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.runtime:runtime-livedata")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-android-compiler:2.50")
    implementation("androidx.hilt:hilt-navigation-fragment:1.2.0")

    // Kakao SDK
    implementation("com.kakao.sdk:v2-user:2.19.0") // 카카오 로그인
    implementation("com.kakao.sdk:v2-talk:2.19.0") // 친구, 메시지(카카오톡)
    implementation("com.kakao.sdk:v2-share:2.19.0") // 메시지(카카오톡 공유)

    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // ViewModel and LiveData
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion")

    // ViewPager2
    implementation("androidx.viewpager2:viewpager2:1.0.0")
    
    // RecyclerView
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // EncryptedSharedPreferences (refresh 토큰 암호화 저장)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // 이미지 로딩 (피드 썸네일)
    implementation("io.coil-kt:coil:2.5.0")

    // Socket.IO (module-chat WebSocket)
    implementation("io.socket:socket.io-client:2.1.0") {
        exclude(group = "org.json", module = "json")
    }
}