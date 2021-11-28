package com.github.maartyl.kxs

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf

//@Serializable
//data class GDTst1(val a: String) : Blah()
//
//
//@Serializable
//sealed class Blah


@OptIn(ExperimentalSerializationApi::class)
fun testXSer() {
  //Blah.serializer().serialize()


  val j = Json.encodeToString(Long.serializer(), 111555L)
  println("json:--$j--")

  val a = ProtoBuf.encodeToHexString(Long.serializer(), 111555L)
  println("prot:--$a--")
}
//
//
//class Enc : AbstractEncoder(){
//  override val serializersModule: SerializersModule
//    get() = TODO("Not yet implemented")
//
//}