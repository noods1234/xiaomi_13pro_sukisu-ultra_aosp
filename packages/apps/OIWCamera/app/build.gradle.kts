plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.oiw.camera"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.oiw.camera"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val ksPath = project.findProperty("oiw.release.keystore") as String?
            if (ksPath != null) {
                storeFile = file(ksPath)
                storePassword = project.findProperty("oiw.release.storePassword") as String?
                keyAlias = project.findProperty("oiw.release.keyAlias") as String?
                keyPassword = project.findProperty("oiw.release.keyPassword") as String?
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }

    sourceSets {
        // Tier 1.5 lives in its own directory so tools/verify_compile.sh can compile the pure-JVM
        // tier without Robolectric on the classpath. Gradle runs both together.
        getByName("test") { java.srcDir("src/test/robolectric/java") }
    }

    testOptions {
        // Gives Robolectric the merged manifest/resources/assets. The offline harness has no
        // resource APK, so app assets are unavailable there; locally they are, which makes
        // ./gradlew testDebugUnitTest strictly the stronger run. See docs/TEST_PLAN.md §0.
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.google.code.gson:gson:2.11.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.12.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
