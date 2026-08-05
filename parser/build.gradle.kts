plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(libs.kotlinx.datetime)

    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.junit5.jupiter)
    testImplementation(libs.turbine)
}

tasks.test {
    useJUnitPlatform()
}
