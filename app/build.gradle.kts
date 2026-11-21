import java.util.zip.GZIPInputStream

plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace = "com.agentfold.app"; compileSdk = 35
 defaultConfig { applicationId = "com.agentfold.app"; minSdk = 29; targetSdk = 35; versionCode = 11; versionName = "0.7.0"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
 val ownerStore = System.getenv("AGENTFOLD_SIGNING_STORE_PATH")
 if (!ownerStore.isNullOrBlank()) {
  signingConfigs.create("ownerManaged") {
   storeFile = file(ownerStore)
   storePassword = requireNotNull(System.getenv("AGENTFOLD_SIGNING_STORE_PASSWORD"))
   keyAlias = requireNotNull(System.getenv("AGENTFOLD_SIGNING_KEY_ALIAS"))
   keyPassword = requireNotNull(System.getenv("AGENTFOLD_SIGNING_KEY_PASSWORD"))
   require(!storePassword.isNullOrBlank() && !keyAlias.isNullOrBlank() && !keyPassword.isNullOrBlank()) { "Incomplete owner signing configuration" }
  }
 }
 buildTypes {
  debug {
   if (!ownerStore.isNullOrBlank()) signingConfig = signingConfigs.getByName("ownerManaged")
   applicationIdSuffix = ".recovery"
   resValue("string", "app_name", "Agent Fold Recovery")
   resValue("string", "control_name", "Agent Fold Recovery control")
  }
  release { isMinifyEnabled = false }
 }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
dependencies {
 implementation(project(":phyphox"))
 implementation("androidx.annotation:annotation:1.9.1")
 testImplementation("junit:junit:4.13.2")
 androidTestImplementation("androidx.test:runner:1.6.2")
 androidTestImplementation("androidx.test:core:1.6.1")
 androidTestImplementation("androidx.test.ext:junit:1.2.1")
}

val unpackBundledFont by tasks.registering {
 val archive = layout.projectDirectory.file("font-source/shippori_mincho_b1_extra_bold.ttf.gz")
 val font = layout.projectDirectory.file("src/main/res/font/shippori_mincho_b1_extra_bold.ttf")
 inputs.file(archive)
 outputs.file(font)
 doLast {
  font.asFile.parentFile.mkdirs()
  GZIPInputStream(archive.asFile.inputStream()).use { input ->
   font.asFile.outputStream().use { output -> input.copyTo(output) }
  }
 }
}
tasks.named("preBuild") { dependsOn(unpackBundledFont) }

