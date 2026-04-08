plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.tangem.tangem_demo"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tangem.tangem_demo"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        viewBinding = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    // internal
    implementation(project(":tangem-sdk-android"))
    implementation(project(":tangem-sdk-core"))

    // androidx
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.material)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.viewpager2)

    // testing
    testImplementation(libs.test.junit4)
    androidTestImplementation(libs.test.androidx.espresso)
    androidTestImplementation(libs.test.androidx.extJunit)
}