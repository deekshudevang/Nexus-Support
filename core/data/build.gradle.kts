import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
}

val localProps = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProps.load(it) }
}
val gatewayDevKey: String = (project.findProperty("MESHLINK_GATEWAY_KEY") as? String)
    ?: localProps.getProperty("meshlink.gateway.key", "meshlink_gw_dev_secret")
val backendDevUrl: String = (project.findProperty("MESHLINK_BACKEND_URL") as? String)
    ?: localProps.getProperty("meshlink.backend.url", "http://10.0.2.2:8000/")
val backendStagingUrl: String = (project.findProperty("MESHLINK_STAGING_URL") as? String)
    ?: localProps.getProperty("meshlink.staging.url", "https://staging.meshlink.app/")
val backendProdUrl: String = (project.findProperty("MESHLINK_PROD_URL") as? String)
    ?: localProps.getProperty("meshlink.prod.url", "https://api.meshlink.app/")

android {
    namespace = "com.meshlink.app.data"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            buildConfigField("String", "GATEWAY_KEY", "\"${project.findProperty("MESHLINK_GATEWAY_KEY") ?: ""}\"")
            buildConfigField("String", "BACKEND_BASE_URL", "\"$backendProdUrl\"")
            buildConfigField("boolean", "ALLOW_CLEARTEXT_HTTP", "false")
        }
        create("staging") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug", "release")
            buildConfigField("String", "GATEWAY_KEY", "\"${project.findProperty("MESHLINK_STAGING_GATEWAY_KEY") ?: gatewayDevKey}\"")
            buildConfigField("String", "BACKEND_BASE_URL", "\"$backendStagingUrl\"")
            buildConfigField("boolean", "ALLOW_CLEARTEXT_HTTP", "false")
        }
        debug {
            buildConfigField("String", "GATEWAY_KEY", "\"$gatewayDevKey\"")
            buildConfigField("String", "BACKEND_BASE_URL", "\"$backendDevUrl\"")
            buildConfigField("boolean", "ALLOW_CLEARTEXT_HTTP", "true")
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("test").assets.srcDirs("$projectDir/schemas")
    }

    buildFeatures {
        buildConfig = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:crypto"))

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)


    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.timber)
    
    api(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.sqlcipher.android)
    implementation(libs.tink.android)
    api(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.testing)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
