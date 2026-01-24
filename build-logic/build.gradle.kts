plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain {
        // NOTE: Kotlin does not yet support 25 JDK target, it will fall back to 24 but is safe to ignore
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}
