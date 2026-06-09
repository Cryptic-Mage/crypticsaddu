import java.util.Properties

plugins {
    alias(libs.plugins.android.app)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
}

val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }
        ?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.helucryptic.android"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.helucryptic.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "com.helucryptic.android.HiltTestRunner"
        buildConfigField("String", "SIGNALING_URL",    "\"${localProps["HELUCRYPTIC_SIGNALING_URL"]   ?: ""}\"")
        buildConfigField("String", "SERVER_PASSWORD",  "\"${localProps["HELUCRYPTIC_SERVER_PASSWORD"] ?: ""}\"")
        buildConfigField("String", "TURN_URL",         "\"${localProps["HELUCRYPTIC_TURN_URL"]        ?: ""}\"")
        buildConfigField("String", "TURN_USERNAME",    "\"${localProps["HELUCRYPTIC_TURN_USERNAME"]   ?: ""}\"")
        buildConfigField("String", "TURN_PASSWORD",    "\"${localProps["HELUCRYPTIC_TURN_PASSWORD"]   ?: ""}\"")
        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf("room.schemaLocation" to "$projectDir/schemas")
            }
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildTypes {
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling)
    implementation(libs.activity.compose)
    implementation(libs.nav.compose)
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.hilt.nav.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)
    implementation(libs.datastore)
    implementation(libs.security.crypto)
    implementation(libs.okhttp)
    implementation(libs.bouncycastle)
    implementation(libs.coroutines.android)
    implementation(libs.zxing)
    implementation(libs.stream.webrtc)

    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.test)
    androidTestImplementation(libs.hilt.testing)
    kaptAndroidTest(libs.hilt.compiler)
}

tasks.withType<Test> { useJUnitPlatform() }
