import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.8.20"
    kotlin("plugin.serialization") version "1.8.20"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

val miraiVersion = "2.15.0-M1"

repositories {
    mavenCentral()
}

dependencies {
    api("net.mamoe", "mirai-core-api", miraiVersion)
    implementation("org.jsoup:jsoup:1.15.4")
    runtimeOnly("net.mamoe", "mirai-core", miraiVersion)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.0")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

application {
    mainClass.set("MainKt")
}