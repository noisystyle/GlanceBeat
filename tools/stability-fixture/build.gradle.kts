plugins { id("com.android.application") version "9.4.0" }
android {
 namespace="it.noxia.ambientplayer.stabilityfixture"
 compileSdk=36
 defaultConfig { applicationId="it.noxia.ambientplayer.stabilityfixture"; minSdk=31; targetSdk=36; versionCode=1; versionName="fixture" }
 compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
}
