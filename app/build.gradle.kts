plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.roborazzi)
}

room { schemaDirectory("$projectDir/schemas") }

android {
    namespace = "com.jlptstudy.app"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.jlptstudy.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }
    buildFeatures { compose = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:srs"))
    implementation(project(":core:quiz"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
}

// 매니페스트 테스트가 병합된 매니페스트를 읽는다.
tasks.matching { it.name == "testDebugUnitTest" }.configureEach { dependsOn("processDebugMainManifest") }

// 스크린샷 테스트가 프로젝트 루트 screenshots/에 PNG를 쓴다(ui-design-review 3절). 기록은 recordRoborazziDebug, 비교는 verifyRoborazziDebug.
tasks.withType<Test>().configureEach {
    systemProperty("screenshots.dir", rootProject.file("screenshots").absolutePath)
    systemProperty("app.dir", projectDir.absolutePath)
    inputs.file(rootProject.file("docs/content/manifest.json"))
    inputs.file(rootProject.file("gradle/libs.versions.toml"))
    systemProperty("app.build.dir", layout.buildDirectory.get().asFile.absolutePath)
}
