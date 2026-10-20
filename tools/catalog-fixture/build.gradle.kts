plugins { id("com.android.application") version "9.4.0" }
android {
 namespace="it.noxia.musicfixture"
 compileSdk=36
 defaultConfig { minSdk=31;targetSdk=36;versionCode=1;versionName="silent-fixture" }
 flavorDimensions += "provider"
 productFlavors {
  create("spotify") { dimension="provider";applicationId="com.spotify.music" }
  create("apple") { dimension="provider";applicationId="com.apple.android.music" }
  create("tidal") { dimension="provider";applicationId="com.aspiro.tidal" }
  create("deezer") { dimension="provider";applicationId="deezer.android.app" }
  create("amazon") { dimension="provider";applicationId="com.amazon.mp3" }
 }
 compileOptions { sourceCompatibility=JavaVersion.VERSION_17;targetCompatibility=JavaVersion.VERSION_17 }
}
dependencies { implementation("androidx.media:media:1.8.0") }
