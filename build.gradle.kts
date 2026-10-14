buildscript {
    // Pin vulnerable transitive Android Gradle Plugin / Dokka classpath modules
    // to patched releases. These overrides affect build tooling only.
    configurations.classpath {
        resolutionStrategy.force(
            "org.bouncycastle:bcpkix-jdk18on:1.86",
            "org.bouncycastle:bcprov-jdk18on:1.86",
            "org.bouncycastle:bcutil-jdk18on:1.86",
            "org.jdom:jdom2:2.0.6.1",
            "org.apache.commons:commons-lang3:3.20.0",
            "org.bitbucket.b_c:jose4j:0.9.7",
            "com.fasterxml.jackson.core:jackson-annotations:2.18.11",
            "com.fasterxml.jackson.core:jackson-core:2.18.11",
            "com.fasterxml.jackson.core:jackson-databind:2.18.11",
            "com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.18.11",
            "com.fasterxml.jackson.module:jackson-module-kotlin:2.18.11",
        )
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.android.multiplatform.library) apply false
    alias(libs.plugins.buildkonfig) apply false // Universal build config
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose.multiplatform) apply false
}
