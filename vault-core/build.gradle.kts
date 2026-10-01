plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("app.keemobile:kotpass:0.13.0")
    // Kotpass publishes Okio as runtime-only, while its model exposes ByteString.
    implementation("com.squareup.okio:okio-jvm:3.15.0")
    testImplementation(kotlin("test-junit"))
}

tasks.test {
    maxHeapSize = "1g"
}
