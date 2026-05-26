plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.kapt)
}

apply(from = "$rootDir/upload-github.gradle.kts")

configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    // crypto
    implementation(libs.crypto.bignum)
    implementation(libs.crypto.eddsa)
    implementation(libs.crypto.kbls)
    implementation(libs.crypto.spongycastle.core)
    implementation(libs.crypto.spongycastle.prov)

    // kotlin
    implementation(libs.kotlin.coroutines.core)
    implementation(libs.kotlin.coroutines.coreJvm)
    implementation(libs.kotlin.reflect)

    // network
    implementation(libs.network.moshi)
    implementation(libs.network.moshi.kotlin)
    kapt(libs.network.moshi.codegen)
    implementation(libs.network.okhttp.logging)
    implementation(libs.network.retrofit)
    implementation(libs.network.retrofit.moshi)

    // testing
    testImplementation(libs.test.junit5)
    testImplementation(libs.test.kotlin)
    testImplementation(libs.test.kotlin.coroutines)
    testImplementation(libs.test.mockk)
    testImplementation(libs.test.truth)
}