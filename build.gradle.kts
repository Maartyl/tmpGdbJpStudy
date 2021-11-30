import org.jetbrains.compose.compose
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  //kotlin("jvm") version "1.6.0"
  kotlin("jvm") version "1.5.31" //kept back for Compose

  id("org.jetbrains.compose") version "1.0.0-rc6"

  //CANNOT use serialization together with compose in the same module (bug)
  // - if needed: need 2 separate modules, one with seri, one with compose
  //kotlin("plugin.serialization") version "1.5.31"
}

group = "github.maartyl"
version = "1.0"

repositories {
  google()
  mavenCentral()
  maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
  implementation(project("gdbapi"))

  implementation(project("gdbjxm"))
  implementation(project("jpModel"))

  implementation(compose.desktop.currentOs)
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0-RC")
  implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.4")

  //useful for EDITING manually of data - can just make json, manually edit, save
  //implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.1")
}

allprojects {
  tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
    kotlinOptions.freeCompilerArgs += arrayOf("-Xopt-in=kotlin.RequiresOptIn")
  }

//  kotlin.sourceSets.all {
//    languageSettings.optIn("kotlin.RequiresOptIn")
//  }
}

compose.desktop {
  application {
    mainClass = "MainKt"
    nativeDistributions {
      targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
      packageName = "jpDepStudy"
      packageVersion = "1.0.0"
    }
  }
}