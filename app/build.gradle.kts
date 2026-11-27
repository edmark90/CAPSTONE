plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.gms.google.services)
    id("kotlin-kapt")

    id("io.sentry.android.gradle") version "6.18.0"
}



android {
    namespace = "com.example.smartplantcare"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.smartplantcare"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Load API keys from local.properties
        val localPropertiesFile = rootProject.file("local.properties")
        val localPropsLines = if (localPropertiesFile.exists()) localPropertiesFile.readLines() else emptyList()
        fun readLocalProp(key: String): String =
            localPropsLines.firstOrNull { it.startsWith("$key=") }
                ?.substringAfter("=")
                ?.trim()
                ?: ""

        val plantNetApiKey = readLocalProp("PLANTNET_API_KEY")
        val cloudinaryCloudName = readLocalProp("CLOUDINARY_CLOUD_NAME")
        val cloudinaryApiKey = readLocalProp("CLOUDINARY_API_KEY")
        val cloudinaryApiSecret = readLocalProp("CLOUDINARY_API_SECRET")

        buildConfigField("String", "PLANTNET_API_KEY", "\"${plantNetApiKey}\"")
        buildConfigField("String", "CLOUDINARY_CLOUD_NAME", "\"${cloudinaryCloudName}\"")
        buildConfigField("String", "CLOUDINARY_API_KEY", "\"${cloudinaryApiKey}\"")
        buildConfigField("String", "CLOUDINARY_API_SECRET", "\"${cloudinaryApiSecret}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        mlModelBinding = true
        buildConfig = true   // kailangan ito para gumana ang buildConfigField sa itaas
    }
}

dependencies {

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation(libs.androidx.foundation)
    val cameraxVersion = "1.3.4"
    val roomVersion = "2.7.0-alpha12"
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("com.google.guava:guava:31.1-android")
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("androidx.compose.material:material-icons-extended:1.6.8")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    // Room Database
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion")
    implementation("androidx.room:room-paging:$roomVersion")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.database.ktx)
    implementation(libs.firebase.messaging.ktx)
    implementation(libs.firebase.functions.ktx)
    implementation(libs.play.services.auth)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

sentry {
    org.set("none-mgr")
    projectName.set("android")

    // this will upload your source code to Sentry to show it as part of the stack traces
    // disable if you don't want to expose your sources
    includeSourceContext.set(true)
}
