plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.detekt)
}

configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
    parallel = true
    ignoreFailures = false
    autoCorrect = true
    buildUponDefaultConfig = true
    config.setFrom(rootProject.files("tangem-android-tools/detekt-config.yml"))
    baseline = rootProject.file("detekt-baseline.xml")
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt> {
    setSource(file(projectDir))
    include("**/*.kt")
    exclude("**/resources/**", "**/build/**")
    reports {
        sarif.required.set(false)
        txt.required.set(true)
    }
}

tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask> {
    setSource(file(projectDir))
    include("**/*.kt")
    exclude("**/resources/**", "**/build/**")
}

dependencies {
    detektPlugins(libs.detekt.formatting)
    detektPlugins(libs.detekt.compose)
}