import java.util.Properties
plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
fun safe(v: String) = v.replace("\\", "\\\\").replace("\"", "\\\"")
val sbUrl = providers.gradleProperty("supabaseUrl").orElse("").get()
val sbKey = providers.gradleProperty("supabaseKey").orElse("").get()
android {
 namespace = "com.gymbuddy.nativeapp"
 compileSdk = 35
 defaultConfig {
  applicationId = "com.gymbuddy.nativeapp"
  minSdk = 26
  targetSdk = 35
  versionCode = 1
  versionName = "0.1.0"
  buildConfigField("String","SUPABASE_URL","\""+safe(sbUrl)+"\"")
  buildConfigField("String","SUPABASE_KEY","\""+safe(sbKey)+"\"")
 }
 buildFeatures { compose = true; buildConfig = true }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
dependencies {
 implementation(platform("androidx.compose:compose-bom:2024.10.01"))
 implementation("androidx.activity:activity-compose:1.9.3")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.material:material-icons-extended")
 implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
 implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
 implementation("com.squareup.okhttp3:okhttp:4.12.0")
 implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
 implementation("androidx.security:security-crypto:1.1.0-alpha06")
}
