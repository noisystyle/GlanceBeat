plugins { id("com.android.application") }
val publicSigningNames = listOf("GLANCEBEAT_KEYSTORE", "GLANCEBEAT_KEYSTORE_PASSWORD", "GLANCEBEAT_KEY_ALIAS", "GLANCEBEAT_KEY_PASSWORD")
val publicSigning = publicSigningNames.associateWith { providers.environmentVariable(it).orNull }
val hasPublicSigning = publicSigning.values.all { !it.isNullOrBlank() }
require(publicSigning.values.all { it.isNullOrBlank() } || hasPublicSigning) { "Provide all four GLANCEBEAT signing environment variables, or none." }
android {
    namespace = "it.noxia.ambientplayer"
    compileSdk { version = release(37) { minorApiLevel = 0 } }
    defaultConfig {
        applicationId = "it.noxia.ambientplayer"
        minSdk = 31
        targetSdk = 37
        versionCode = 51
        versionName = "0.29.0"
        testInstrumentationRunner = "it.noxia.ambientplayer.DrivingInstrumentation"
    }
    buildFeatures { buildConfig = true }
    // Ship both small translations together, including offline app-language changes.
    bundle { language { enableSplit = false } }
    signingConfigs {
        if (hasPublicSigning) {
            create("publicRelease") {
                storeFile = file(publicSigning.getValue("GLANCEBEAT_KEYSTORE")!!)
                storePassword = publicSigning.getValue("GLANCEBEAT_KEYSTORE_PASSWORD")
                keyAlias = publicSigning.getValue("GLANCEBEAT_KEY_ALIAS")
                keyPassword = publicSigning.getValue("GLANCEBEAT_KEY_PASSWORD")
            }
        }
    }
    flavorDimensions += "edition"
    productFlavors {
        create("public") {
            dimension = "edition"
            applicationId = "it.noxia.glancebeat"
            versionCode = 2
            versionName = "0.1.0-beta02"
            if (hasPublicSigning) signingConfig = signingConfigs.getByName("publicRelease")
            isDefault = true
        }
        // The personal source set stays local and is omitted from public exports.
        if (file("src/personal/java/it/noxia/ambientplayer/AccessorySupport.java").isFile) {
            create("personal") {
                dimension = "edition"
                // Preserve the existing installation and preferences.
                applicationId = "it.noxia.ambientplayer"
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.media:media:1.8.0")
    testImplementation("junit:junit:4.13.2")
}
