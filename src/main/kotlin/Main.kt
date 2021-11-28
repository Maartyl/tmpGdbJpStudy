// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.github.maartyl.gdb.jxm.gdbJxmOpen
import com.github.maartyl.jpmodel.Test1
import com.github.maartyl.jpmodel.Test2
import com.github.maartyl.jpmodel.Test3
import com.github.maartyl.jpmodel.TestSeri
import com.github.maartyl.kxs.testXSer
import com.github.maartyl.testmapdb.test1
import kotlinx.coroutines.*
import org.mapdb.DBMaker

@Composable
@Preview
fun App() {
  var text by remember { mutableStateOf("Hello, World!") }

  MaterialTheme {
    Button(onClick = {
      text = "Hello, Desktop!"
    }) {
      Text(text)
    }
  }
}

fun main() = application {
  remember {
    test1()
    testXSer()
  }
  LaunchedEffect(Unit) {
    withContext(Dispatchers.IO) {
      val db = DBMaker.fileDB("tmpMapDB")
        .transactionEnable()
        .make()
      val gdb = gdbJxmOpen(this, TestSeri.serializer(), db).build()
      launch { gdb.read { delay(1000) } }
      launch { gdb.read { delay(1000) } }
      launch { gdb.read { delay(200) } }
      delay(300)
      //I expect to see 2 in output
      gdb.mutate {
        val ti = insertNew(Test1("asd0"))
        val a = insertNew(Test2("beheme", 554))

        println("$a :deref: ${deref(a)}")

        val b = insertNew(Test3("qq", ti))
        println(
          "$b :deref: ${deref(b)} ; was $ti :deref: ${deref(ti)} ; is ${
            deref(b)?.ti?.let { deref(it) }
          }"
        )
      }
      db.commit()

      awaitCancellation()
    }

  }
  Window(onCloseRequest = ::exitApplication) {
    App()
  }
}
