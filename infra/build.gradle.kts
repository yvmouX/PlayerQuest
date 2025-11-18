plugins {
    kotlin("jvm")
}
dependencies {
    implementation(project(":common"))
    implementation(project(":api"))
    implementation(project(":core"))
    implementation(kotlin("stdlib-jdk8"))
}
repositories {
    mavenCentral()
}
kotlin {
    jvmToolchain(21)
}