plugins {
  kotlin("jvm")

  kotlin("plugin.serialization") version "1.6.0"
}

group = "github.maartyl"
version = "1.0"

repositories {
  mavenCentral()
}

dependencies {
  implementation(kotlin("stdlib"))

//  implementation(project(":gdbapi"))
//  implementation(project(":gdbjxm"))

  implementation("com.github.maartyl.gdb:gdbapi:+")
  implementation("com.github.maartyl.gdb:gdbjxm:+")

  implementation(project(":jpModel"))

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")
  implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.4")

  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.1")
  //implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.3.1")
}