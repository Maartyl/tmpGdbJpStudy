pluginManagement {
  repositories {
    google()
    gradlePluginPortal()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
  }

}
rootProject.name = "jpDepStudy"

//gdb impl : Java kotlinX-seri MapDB
include("gdbjxm")
include("gdbapi")
include("jpModel")
include("kanji_import")
