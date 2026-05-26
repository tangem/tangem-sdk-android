plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

apply(from = "$rootDir/upload-github.gradle.kts")

android {
    namespace = "com.tangem.sdk"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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

tasks.register<Jar>("sourceJar") {
    from(android.sourceSets["main"].java.srcDirs)
    archiveClassifier.set("sources")
}

dependencies {
    // internal
    implementation(project(":tangem-sdk-core"))

    // androidx
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.material)

    // kotlin
    implementation(libs.kotlin.coroutines.core)
    implementation(libs.kotlin.coroutines.coreJvm)
    implementation(libs.kotlin.reflect)

    // misc
    implementation(libs.armadillo)
    implementation(libs.rippleBackground)

    // testing
    testImplementation(libs.test.junit4)
    androidTestImplementation(libs.test.androidx.espresso)
    androidTestImplementation(libs.test.androidx.runner)
}