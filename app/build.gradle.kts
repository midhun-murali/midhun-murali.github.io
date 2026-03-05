import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

android {
    // Load keystore properties if available
    val keystorePropertiesFile = rootProject.file("app/keystore.properties")
    val keystoreProperties = Properties()
    if (keystorePropertiesFile.exists()) {
        keystoreProperties.load(keystorePropertiesFile.inputStream())
    }

    namespace = "com.beauty.camera.selfie.camera.filter"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.beauty.camera.selfie.camera.filter"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Add flavor dimension and product flavors
    flavorDimensions += "version"
    productFlavors {
        create("beautyFilter") {
            dimension = "version"
        }
        create("bubbleSoft") {
            dimension = "version"
            applicationIdSuffix = ".bubblesoft"
        }
        create("sideRail") {
            dimension = "version"
            applicationIdSuffix = ".siderail"
        }
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.play.services.ads)
    implementation(libs.camera.core)
    implementation(libs.androidx.recyclerview)
    // ExifInterface for reading/writing image orientation
    implementation("androidx.exifinterface:exifinterface:1.3.6")
    // uCrop for cropping UI
    implementation("com.github.yalantis:ucrop:2.2.8")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}