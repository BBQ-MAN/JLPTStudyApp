import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// 모의고사·문제 풀이 진행과 채점. Android 없이 JVM 테스트로 검증한다(compose-app-dev 2절).
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_17 } }

dependencies {
    testImplementation(libs.junit)
}
