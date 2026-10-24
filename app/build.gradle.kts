plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace = "com.photorevive.android"
 compileSdk = 35
 defaultConfig {
  applicationId = "com.photorevive.android"; minSdk = 29; targetSdk = 35
  versionCode = 2; versionName = "1.1"
  testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
 }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
dependencies {
 implementation("androidx.activity:activity-ktx:1.10.1")
 implementation("androidx.documentfile:documentfile:1.0.1")
 implementation("androidx.exifinterface:exifinterface:1.4.1")
 implementation("org.opencv:opencv:4.12.0")
 testImplementation("junit:junit:4.13.2")
 androidTestImplementation("androidx.test:runner:1.6.2")
 androidTestImplementation("androidx.test:rules:1.6.1")
 androidTestImplementation("androidx.test.ext:junit:1.2.1")
 androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
