plugins { id("com.android.application") version "9.4.0" }
android {
 namespace="app.morphe.android.apps.youtube.music"
 compileSdk=36
 defaultConfig { applicationId="app.morphe.android.apps.youtube.music"; minSdk=31; targetSdk=36; versionCode=1; versionName="fixture" }
 compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
}

dependencies { implementation("androidx.media:media:1.8.0") }
