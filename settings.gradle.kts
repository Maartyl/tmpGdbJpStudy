pluginManagement {
  repositories {
    google()
    gradlePluginPortal()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
  }

}
rootProject.name = "jpDepStudy"

include("jpModel")
include("kanji_import")

//for group: com.github.maartyl.gdb
includeBuild("../maa-gdb")