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

// 更新检查地址：从根目录 update.properties 读取（已 gitignore，服务器路径不入库）。
// 缺失时 debug/测试回落占位地址仅作开发；release 构建直接失败——占位地址烧进正式包无法挽回。
val updateProps = Properties().apply {
    val f = rootProject.file("update.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val manifestUrl: String? = updateProps.getProperty("manifestUrl")?.takeIf { it.isNotBlank() }
if (manifestUrl == null) {
    logger.warn("update.properties 缺失或未配置 manifestUrl：本次构建使用占位更新地址（仅限开发，勿发布）")
}

android {
    namespace = "com.battor.freshmate"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.battor.freshmate"
        minSdk = 29
        targetSdk = 36
        versionCode = 8
        versionName = "0.2.6"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField(
            "String",
            "UPDATE_MANIFEST_URL",
            "\"${manifestUrl ?: "https://example.com/freshmate/manifest.json"}\"",
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

// release 门禁：更新地址未配置即中止（占位地址烧进正式包后无法挽回），并给出修复指导
tasks.matching { it.name.contains("Release") }.configureEach {
    if (manifestUrl == null) {
        doFirst {
            throw GradleException(
                """
                |
                |正式构建中止：缺少更新检查地址配置。
                |请新建文件 ${rootProject.file("update.properties").absolutePath}
                |内容一行：
                |    manifestUrl=https://你的域名/freshmate/manifest.json
                |（该文件已在 .gitignore 中，不会被提交）
                |
                """.trimMargin()
            )
        }
    }
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
