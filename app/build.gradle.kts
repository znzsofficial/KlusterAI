import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.nekolaska.klusterai"
    compileSdk = 36

    val versionPropsFile = file("version.properties")
    val versionProps = Properties()
    versionProps.load(FileInputStream(versionPropsFile))
    val verCode = Integer.parseInt(versionProps["VERSION_CODE"] as String)
    if (":app:assembleRelease" in gradle.startParameter.taskNames) {
        versionProps["VERSION_CODE"] = (verCode + 1).toString()
        versionProps.store(versionPropsFile.writer(), null)
    }

    defaultConfig {
        applicationId = "com.nekolaska.klusterai"
        minSdk = 24
        targetSdk = 36
        versionCode = verCode
        versionName = "0.$verCode"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    configurations.all {
        exclude(group = "io.coil-kt")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    applicationVariants.all {
        outputs.all {
            val apkName = "KlusterAI_${defaultConfig.versionName}.APK"
            (this as BaseVariantOutputImpl).outputFileName = apkName
        }
    }
}

dependencies {
    // markdown modified from implementation(libs.compose.markdown)
    implementation(libs.markwon)
    implementation(libs.markwon.core)
    implementation(libs.markwon.ext.strikethrough)
    implementation(libs.markwon.ext.tables)
    implementation(libs.markwon.html)
    implementation(libs.markwon.linkify)
    implementation(libs.markwon.ext.tasklist)

    // image loader for markdown
    implementation(libs.coil3)
    implementation(libs.coil3.network.okhttp)
    implementation(libs.coil3.gif)
    implementation(libs.okhttp)

    //replaced with kotlinx.serialization
    //implementation(libs.json)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    // appcompat for markdown
    implementation(libs.androidx.appcompat)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}