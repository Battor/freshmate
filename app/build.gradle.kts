import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// 正式签名配置：从根目录 keystore.properties 读取（已 gitignore，密码不入库）
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// 更新检查地址：优先从根目录 update.properties 读取（已 gitignore，本地可覆盖）。
// 缺失时回落入库的公开默认地址——F-Droid 等从公开源码构建的环境没有该文件，
// 必须能直接出正式包（此地址本来就会烧进每个 APK 的 BuildConfig，入库无泄露问题）。
val DEFAULT_MANIFEST_URL = "https://www.battor.site/freshmate/manifest.json"
val updateProps = Properties().apply {
    val f = rootProject.file("update.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val manifestUrl: String =
    updateProps.getProperty("manifestUrl")?.takeIf { it.isNotBlank() } ?: DEFAULT_MANIFEST_URL
if (updateProps.getProperty("manifestUrl").isNullOrBlank()) {
    logger.warn("update.properties 缺失或未配置 manifestUrl：使用入库默认更新地址 $DEFAULT_MANIFEST_URL")
}

android {
    namespace = "com.battor.freshmate"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.battor.freshmate"
        minSdk = 29
        targetSdk = 36
        versionCode = 9
        versionName = "0.2.7"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField(
            "String",
            "UPDATE_MANIFEST_URL",
            "\"$manifestUrl\"",
        )
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProps["storeFile"] as String)
                storePassword = keystoreProps["storePassword"] as String
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 关闭 VCS 信息嵌入（AGP 8.3+ 默认开启）：它把当前 git 提交哈希写进 APK，
            // 使同内容不同提交/checkout 方式的构建哈希不同，与 F-Droid 可复现构建校验冲突
            vcsInfo {
                include = false
            }
            if (keystoreProps.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // 关闭依赖元数据签名块（AGP 默认开启）：它会被 F-Droid 扫描器判为多余签名块，
    // 且内容含依赖信息，与可复现构建的最小化原则相悖
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    sourceSets {
        getByName("androidTest").assets.srcDirs(files("$projectDir/schemas"))
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // AndroidX
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    // Room
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Local tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("androidx.test:core:1.6.1")

    // Instrumented tests
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.room:room-testing:2.8.4")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    // v2：日志 / 更新 / 导航
    implementation("com.jakewharton.timber:timber:5.0.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("androidx.navigation:navigation-compose:2.8.1")
    implementation("androidx.core:core:1.13.1")

    // 需求-4：设置（DataStore）/ per-app 语言（appcompat）
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Tooling / test manifest
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
