import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm {}
    sourceSets {
        jvmMain.dependencies {
            implementation(libs.bundles.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.kotlinx.collections.immutable)
            implementation(compose.desktop.currentOs) {
                // compose.desktop.currentOs imports the wrong material 2, so we exclude it
                exclude(group = "org.jetbrains.compose.material", module = "material")
            }
            implementation(project(":shared"))
            implementation(project(":library"))
            implementation(libs.nicehttp)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.jackson.module.kotlin)
            implementation("uk.co.caprica:vlcj:4.8.3")
            implementation("de.femtopedia.dex2jar:dex-tools:2.4.20")
            implementation("org.ow2.asm:asm:9.9")
            implementation("org.ow2.asm:asm-commons:9.9")
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.junit)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// java.lang.System::load has been called by org.jetbrains.skiko.LibraryLoader in an unnamed module
tasks.withType<JavaExec> {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

compose.desktop {
    application {
        mainClass = "com.lagradost.cloudstream4.MainKt"
        jvmArgs(
            "--enable-native-access=ALL-UNNAMED"
        )
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "CloudStream"
            packageVersion = "1.0.1"
            modules(
                "java.instrument",
                "jdk.unsupported",
                "jdk.zipfs",
                "java.net.http",
                "java.sql",
                "java.naming",
                "java.management",
                "java.security.jgss",
                "java.security.sasl"
            )

            val iconsRoot = project.file("src/desktop-icons")
            macOS {
                // iconFile.set(iconsRoot.resolve("icon-mac.icns"))
            }
            windows {
                iconFile.set(iconsRoot.resolve("icon-windows.ico"))
                // Per-user install: lands in LocalAppData (writable, no admin needed)
                // so settings + extensions work out of the box. dirChooser still
                // lets users pick another folder (e.g. D:) at install time.
                perUserInstall = true
                dirChooser = true
                // Create a CloudStream desktop shortcut during installation.
                shortcut = true
                // menuGroup = "Compose Examples"
                // see https://wixtoolset.org/documentation/manual/v3/howtos/general/generate_guids.html
                // upgradeUuid = ""
            }
            linux {
                iconFile.set(iconsRoot.resolve("icon-linux.png"))
            }
        }

        //buildTypes.release.proguard {
        //    configurationFiles.from(project.file("rules.pro"))
        //}
    }
}
