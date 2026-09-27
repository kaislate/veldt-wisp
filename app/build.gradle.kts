import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    id("com.google.dagger.hilt.android")
    id("kotlin-kapt")
}

android {
    namespace = "com.kaislate.veldt"
    compileSdk = 36

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "2.2.0"
    }

    defaultConfig {
        applicationId = "com.kaislate.veldt"
        minSdk = 29
        targetSdk = 36
        versionCode = 34
        versionName = "0.7.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // F-Droid will not ship an app that downloads and installs APKs itself, so the
    // in-app updater exists only in the GitHub build. Both flavors keep the same
    // applicationId on purpose: they are one app from two sources, and a user can move
    // between them when the signing key matches. The updater's code, manifest entries
    // and FileProvider paths live in src/github; src/fdroid supplies a no-op
    // UpdateChecker, so the F-Droid APK has no GitHub-Releases networking compiled in.
    flavorDimensions += "distribution"
    productFlavors {
        create("github") {
            dimension = "distribution"
            buildConfigField("boolean", "UPDATER_ENABLED", "true")
        }
        create("fdroid") {
            dimension = "distribution"
            buildConfigField("boolean", "UPDATER_ENABLED", "false")
        }
    }

    // F-Droid's scanner rejects this encrypted Google-only blob, and omitting it keeps
    // the APK reproducible from source alone.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    val keyProps = Properties().apply {
        val f = rootProject.file("key.properties")
        if (f.exists()) FileInputStream(f).use { load(it) }
    }
    signingConfigs {
        create("release") {
            if (keyProps.isNotEmpty()) {
                storeFile = rootProject.file(keyProps["storeFile"] as String)
                storePassword = keyProps["storePassword"] as String
                keyAlias = keyProps["keyAlias"] as String
                keyPassword = keyProps["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            // Unshrunk, the release APK was 55 MB, 53 MB of it dex, which is too large
            // for F-Droid and IzzyOnDroid. R8 keeps only reachable code and resources.
            // proguard-rules.pro records why no extra keep rules are needed.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keyProps.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {

        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions {
        unitTests {
            // Plain JVM tests exercise classes that log via android.util.Log; without this
            // the stubbed android.jar throws "not mocked" on the first Log.d.
            isReturnDefaultValues = true
        }
    }

    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
            freeCompilerArgs.add("-XXLanguage:+PropertyParamAnnotationDefaultTargetMode")
            freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
        }
    }
}

dependencies {

    // Applied to the app and to the androidTest classpath so both resolve the same
    // Compose release. It is why the Compose entries in libs.versions.toml pin no
    // version of their own.
    implementation(platform(libs.compose.bom))
    androidTestImplementation(platform(libs.compose.bom))

    // Compose itself. ui-tooling is debug-only: @Preview support stays out of release
    // builds, while the preview annotations it needs to compile against do not.
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.tooling)
    implementation(libs.androidx.material3)

    // Nothing under app/src imports androidx.navigation — there is no NavHost, the app
    // is one activity. Kept on the classpath but currently dead weight.
    implementation(libs.androidx.navigation.compose)

    // Dependency injection. kapt runs the annotation processor that emits the Hilt
    // components for @AndroidEntryPoint services and the application class.
    implementation(libs.hilt.android)
    kapt(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Backs SettingsRepository, the single preferences store the app owns.
    implementation(libs.androidx.datastore.preferences)

    // Album-art bitmaps: fetched from the media notification's icon in the listener,
    // drawn by AsyncImage in the expanded panel.
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)

    // Swatch extraction behind ColorExtractor, which turns the current artwork into
    // the pill's tint and the wave gradient.
    implementation(libs.androidx.palette.ktx)

    // On the classpath but unreferenced: sessions are read through the platform
    // android.media.session APIs instead.
    implementation(libs.androidx.media3.session)

    implementation(libs.androidx.material.icons.extended)

    // The overlay's ComposeView hangs off a raw WindowManager window, so no Activity
    // has seeded the owners Compose reads back off the view tree. These three supply
    // the owner interfaces OverlayOwner implements and OverlayWindowManager attaches
    // to every view it adds: lifecycle, view-model store, saved state.
    implementation(libs.androidx.lifecycle.runtime.android)
    implementation(libs.androidx.lifecycle.viewmodel.android)
    implementation(libs.androidx.savedstate)

    implementation(libs.androidx.ui.graphics)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}