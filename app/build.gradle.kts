import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose)
    alias(libs.plugins.serialization)
}


android {
    signingConfigs {
        create("defaultSignature") {
            storeFile = file(project.findProperty("StoreFile") ?: "naq_owndroid.jks")
            storePassword = (project.findProperty("StorePassword") as String?) ?: "naq_owndroid"
            keyPassword = (project.findProperty("KeyPassword") as String?) ?: "naq_owndroid"
            keyAlias = (project.findProperty("KeyAlias") as String?) ?: "naq_owndroid"
        }
    }
    namespace = "com.bintianqi.owndroid"
    compileSdk = 36

    lint.checkReleaseBuilds = false
    lint.disable += "All"

    defaultConfig {
        applicationId = "com.bintianqi.owndroid"
        minSdk = 22
        targetSdk = 36
        versionCode = 42
        versionName = "7.1.2"
        multiDexEnabled = false
    }

    buildTypes {

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("defaultSignature")
        }
        debug {
            signingConfig = signingConfigs.getByName("defaultSignature")
        }
    }

    // Đặt tên file APK tự động với số thứ tự tăng dần
    applicationVariants.all {
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val buildTypeSuffix = if (buildType.name == "release") "r" else "d"
            val packageName = applicationId.substringAfterLast(".")

            // Lấy số thứ tự từ file counter (đặt ở thư mục gốc project)
            val counterFile = rootProject.file("apk-counter.txt")
            var counter = 1
            if (counterFile.exists()) {
                counter = counterFile.readText().toIntOrNull() ?: 1
            }

            // Tạo tên file
            output.outputFileName = "${versionCode}${buildTypeSuffix}-${packageName}-i${counter}.apk"

            // Tăng counter và lưu lại
           // counterFile.writeText((counter + 1).toString())
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        aidl = true
    }
    androidResources {
        generateLocaleConfig = true
    }
    dependenciesInfo {
        includeInApk = false
    }
    composeCompiler {
        includeSourceInformation = false
        includeTraceMarkers = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
    sourceSets {
        all {
            languageSettings.optIn("kotlinx.serialization.ExperimentalSerializationApi")
        }
    }
}

gradle.taskGraph.whenReady {
    project.tasks.findByPath(":app:test")?.enabled = false
    project.tasks.findByPath(":app:lint")?.enabled = false
    project.tasks.findByPath(":app:lintAnalyzeDebug")?.enabled = false
}

dependencies {
    implementation(libs.androidx.activity.compose)
    // For Preferences DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1") // Or the latest version

    // For Proto DataStore (if you're using that instead)
    // implementation("androidx.datastore:datastore-core:1.1.1")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.accompanist.drawablepainter)
    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.shizuku.provider)
    implementation(libs.shizuku.api)

    implementation(libs.dhizuku.api)
    implementation(libs.dhizuku.server.api)
    implementation(libs.androidx.fragment)
    implementation(libs.hiddenApiBypass)
    implementation(libs.libsu)
    implementation(libs.serialization)
    implementation(libs.workmanager)
    implementation(kotlin("reflect"))


}

