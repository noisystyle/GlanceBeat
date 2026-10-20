plugins { id("com.android.application") version "9.4.0" }
android {
 namespace="com.music.bitchord"
 compileSdk=36
 defaultConfig { applicationId="com.music.bitchord"; minSdk=31; targetSdk=36; versionCode=1; versionName="fixture" }
 compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
}
