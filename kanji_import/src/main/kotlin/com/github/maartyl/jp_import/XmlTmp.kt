package com.github.maartyl.jp_import

import com.github.maartyl.gdb.*
import com.github.maartyl.gdb.jxm.gdbJxmOpen
import kotlinx.coroutines.*
import org.mapdb.DBMaker

//fun foo() {
//  val f = XMLInputFactory.newFactory()
//  val s = f.createXMLStreamReader(InputStream.nullInputStream())
//}

fun main() {

  runBlocking {
    //inspectKeys(this)
    //inspectBigElms(this)
    //inspectFullKeyCounts(this)

    supervisorScope {
//      gdbTestImporting(this)
//      cancel()
      launch {
        gdbTestImporting(this)
        delay(1000)
        cancel()
      }
    }

//    coroutineScope {
//      gdbTestImporting(this)
//      cancel()
//    }

    //readln()
    gdbImporting(this).findVariants()
    cancel()
  }

}

interface GdbImporting : GDb {
  val kdic2: GKDic2
}

class GKDic2(builder: GDbBuilder) {

  val prim = builder.primaryIndex<Kanjidic2>("Kanjidic2") { it.lit }

  val ids = builder.reverseIndexStr<Kanjidic2, String>("kdic2ids", { it }) { k, v, c ->
    v.ids.let { c.addAll(it) }
    Unit
  }

  val hasVariants = builder.reverseIndexLong<Kanjidic2, Unit>("kdic2variants", { 1 }) { k, v, c ->
    if (v.variantsIds.isNotEmpty()) c.add(Unit)
    Unit
  }

}

suspend fun gdbImporting(scope: CoroutineScope): GdbImporting = withContext(Dispatchers.IO) {

//  val db = DBMaker.fileDB("tmpMapDB")
//    .transactionEnable()
//    .make()

  val db = DBMaker.memoryDB()
    .transactionEnable()
    .make()

  val b = gdbJxmOpen(scope, GImporting.serializer(), db)

  val kdic2 = GKDic2(b)

  val gdb = b.build()
  object : GdbImporting, GDb by gdb {
    override val kdic2: GKDic2 = kdic2
  }
}


abstract class GKDic2Test(builder: GDbBuilder) : GDb {

  val prim = builder.primaryIndex<Kanjidic2>("Kanjidic2") { it.lit }

  val ids = builder.reverseIndexStr<Kanjidic2, String>("kdic2ids", { it }) { k, v, c ->
    v.ids.let { c.addAll(it) }
    Unit
  }

  val hasVariants = builder.reverseIndexLong<Kanjidic2, Unit>("kdic2variants", { 1 }) { k, v, c ->
    if (v.variantsIds.isNotEmpty()) c.add(Unit)
    Unit
  }

}

suspend fun gdbTestImporting(scope: CoroutineScope): GKDic2Test = withContext(Dispatchers.IO) {

//  val db = DBMaker.fileDB("tmpMapDB")
//    .transactionEnable()
//    .make()

  val db = DBMaker.memoryDB()
    .transactionEnable()
    .make()

  val b = gdbJxmOpen(scope, GImporting.serializer(), db)

  object : GKDic2Test(b), GDb by b.buildBg(scope) {}
}
