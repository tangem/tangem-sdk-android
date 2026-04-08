buildscript {
    dependencies {
        classpath("com.github.jengelman.gradle.plugins:shadow:6.1.0")
    }
}

plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

apply(plugin = "com.github.johnrengelman.shadow")

configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(project(":tangem-sdk-core"))
    implementation(project(":tangem-sdk-jvm"))
    implementation(libs.kotlin.coroutines.core)
    implementation(libs.kotlin.coroutines.jdk8)
    implementation("commons-cli:commons-cli:1.4")
}

tasks.named<Jar>("jar") {
    manifest {
        attributes("Main-Class" to "AppKt")
    }
    from(configurations.getByName("compileClasspath").map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("tangem-desktop")
    archiveClassifier.set("")
    archiveVersion.set("")
}