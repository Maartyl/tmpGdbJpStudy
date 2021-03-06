plugins {
  kotlin("multiplatform")

  kotlin("plugin.serialization") version "1.6.0"
}

group = "github.maartyl"
version = "1.0"

repositories {
  mavenCentral()
}

kotlin {
  /* Targets configuration omitted.
  *  To find out how to configure the targets, please follow the link:
  *  https://kotlinlang.org/docs/reference/building-mpp-with-gradle.html#setting-up-targets */

  jvm()

  sourceSets {
    val commonMain by getting {
      dependencies {
        implementation(kotlin("stdlib-common"))
        //implementation(project(":gdbapi"))
        implementation("com.github.maartyl.gdb:gdbapi:+")
      }
    }
    val commonTest by getting {
      dependencies {
        implementation(kotlin("test-common"))
        implementation(kotlin("test-annotations-common"))
      }
    }
  }
}