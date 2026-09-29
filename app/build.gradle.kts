plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.baselineprofile)  // PERF-16: embeds baseline-prof.txt into release
}

android {
    namespace = "com.warped"
    compileSdk = 35

    val versionMajor = 1
    val versionMinor = 7
    val versionPatch = 1
    val baseVersionCode = versionMajor * 10000 + versionMinor * 100 + versionPatch // 10701

    // CI build number from GitHub Actions (always increments per workflow run)
    val ciBuildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0
    val versionCode = baseVersionCode * 1000 + ciBuildNumber // e.g. 10701001
    val versionName = "$versionMajor.$versionMinor.$versionPatch"

    defaultConfig {
        applicationId = "com.warped.app"
        minSdk = 28
        targetSdk = 35
        this.versionCode = versionCode
        this.versionName = versionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "LITERTLM_VERSION", "\"${libs.versions.litertlm.get()}\"")

    }

    signingConfigs {
        create("release") {
            // Load signing props from local.properties (not committed to git)
            val propsFile = rootProject.file("local.properties")
            val props = mutableMapOf<String, String>()
            if (propsFile.exists()) {
                propsFile.readLines().forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && trimmed.contains("=")) {
                        val eq = trimmed.indexOf("=")
                        props[trimmed.substring(0, eq).trim()] = trimmed.substring(eq + 1).trim()
                    }
                }
            }
            storeFile = file(props["RELEASE_STORE_FILE"] ?: "keystore/warped-release.jks")
            storePassword = props["RELEASE_STORE_PASSWORD"] ?: ""
            keyAlias = props["RELEASE_KEY_ALIAS"] ?: "warped"
            keyPassword = props["RELEASE_KEY_PASSWORD"] ?: ""
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // StrictMode enabled in Application.onCreate for debug builds
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }

    // Phase 53: expose Room exported schemas (app/schemas/<db-fqn>/*.json) as
    // androidTest assets so MigrationTestHelper.runMigrationsAndValidate can
    // resolve them on-device. assembleDebug regenerates 15.json before the test.
    sourceSets {
        getByName("androidTest").assets.srcDirs(files("$projectDir/schemas"))
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

// AGP 8.x checkDebugClasspath fails with Gradle 8.13 due to Kotlin plugin
// pulling kotlin-reflect 2.2.x. This is a no-op lint task, not needed.
tasks.configureEach {
    if (name.startsWith("check") && name.endsWith("Classpath")) {
        enabled = false
    }
}

dependencies {
    // Force Kotlin library versions to match the compiler
    implementation(platform(libs.kotlin.bom))

    // Compose BOM governs all Compose library versions
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.process)

    // Navigation
    implementation(libs.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    ksp(libs.hilt.androidx.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Networking
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // HTML parsing (parse-only; never Jsoup.connect())
    implementation(libs.jsoup)

    // Coil 3 (OG thumbnails; disk cache = offline story; own OkHttp instance)
    implementation(libs.coil3.compose)
    implementation(libs.coil3.network.okhttp)

    // DataStore
    implementation(libs.datastore.preferences)

    // WorkManager
    implementation(libs.work.runtime.ktx)

    // SplashScreen
    implementation(libs.core.splashscreen)

    // Baseline Profiles (PERF-16): installs the release baseline profile on first run
    implementation(libs.profileinstaller)

    // Security
    implementation(libs.security.crypto)
    implementation(libs.sqlcipher)

    // LiteRT-LM (per LITE-01)
    implementation(libs.litertlm)

    // Highlights — syntax tokenization engine for code highlighting
    implementation(libs.highlights)

    // Immutable collections
    implementation(libs.kotlinx.collections.immutable)

    // Logging
    implementation(libs.timber)
    implementation(libs.errorprone.annotations)

    // Testing
    testImplementation(libs.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.compose.ui.test)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.benchmark.macro.junit4)
    androidTestImplementation(libs.benchmark.junit4)
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

tasks.register<Exec>("auditDependencies") {
    description = "Fails if any Gallery anti-pattern dependency is present (kapt, firebase, moshi, gson, kotlin-reflect, ktor, mcp, tflite, mlkit-genai, appauth, compose-richtext, cameraX, datastore-proto)."
    group = "verification"
    commandLine("./scripts/audit-dependencies.sh")
    workingDir = rootProject.projectDir
    standardOutput = System.out
    errorOutput = System.out
}

tasks.named("check") {
    dependsOn("auditDependencies")
}
