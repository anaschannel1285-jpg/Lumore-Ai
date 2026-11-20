// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.secrets) apply false
  alias(libs.plugins.google.services) apply false
}

// Ensure fresh GitHub clones or CI builds automatically restore the debug keystore
// from the committed debug.keystore.base64 without manual setup.
run {
  val debugKeystore = rootProject.file("debug.keystore")
  val encodedKeystore = rootProject.file("debug.keystore.base64")
  if (!debugKeystore.exists() && encodedKeystore.exists()) {
    val decodedBytes = java.util.Base64.getMimeDecoder().decode(encodedKeystore.readText())
    debugKeystore.writeBytes(decodedBytes)
  }
}
