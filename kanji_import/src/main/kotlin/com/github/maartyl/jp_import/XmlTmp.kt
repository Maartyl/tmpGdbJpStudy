package com.github.maartyl.jp_import

import com.github.maartyl.gdb.GDb
import com.github.maartyl.gdb.GDbBuilder
import com.github.maartyl.gdb.jxm.gdbJxmOpen
import com.github.maartyl.gdb.makeIndexLong
import com.github.maartyl.gdb.makeIndexStr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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

    gdbImporting(this).findVariants()
  }

}

interface GdbImporting : GDb {
  val kdic2: GKDic2
}

class GKDic2(builder: GDbBuilder) {

  val prim = builder.primaryIndex<Kanjidic2>("Kanjidic2") { it.lit }

  val ids = builder.makeIndexStr<Kanjidic2, String>("kdic2ids", { it }) { k, v, c ->
    v.ids.let { c.addAll(it) }
    Unit
  }

  val hasVariants = builder.makeIndexLong<Kanjidic2, Unit>("kdic2variants", { 1 }) { k, v, c ->
    v.variants.let { c.add(Unit) }
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