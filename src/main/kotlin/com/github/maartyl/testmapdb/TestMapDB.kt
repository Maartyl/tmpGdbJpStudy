package com.github.maartyl.testmapdb

import org.mapdb.DBMaker
import org.mapdb.DataInput2
import org.mapdb.DataOutput2
import org.mapdb.Serializer
import org.mapdb.serializer.GroupSerializerObjectArray
import java.io.Serializable

data class TestObj(val a: String, val b: Int) : Serializable

object Seri : GroupSerializerObjectArray<TestObj>() {
  override fun serialize(out: DataOutput2, value: TestObj) {
    out.writeUTF(value.a)
    out.packInt(value.b)
  }

  override fun deserialize(input: DataInput2, available: Int): TestObj {
    return TestObj(input.readUTF(), input.unpackInt())
  }

}

fun test1() {
  val db = DBMaker.memoryDB()
    .transactionEnable()
    .make()

  db.atomicLong("asd")

  //db.treeMap("graphNodes", Serializer.STRING, Serializer.ELSA)
//  db.treeMap("graphNodes", Serializer.STRING, Seri)
  val gn = db.treeMap("graphNodes", Serializer.STRING_DELTA, Seri)
    .createOrOpen()
//  val gn = DB.TreeMapMaker<String, TestObj>(db, "graphNodes")
//    .createOrOpen()

  gn["hello"] = TestObj("asd", 42)
  println(gn["hello"])
  db.close()
}