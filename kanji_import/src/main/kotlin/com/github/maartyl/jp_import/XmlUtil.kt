package com.github.maartyl.jp_import

//these are meant for parsing using SAX or other streaming input

//for XML that has MANY "roots" - this is one of such roots
// - it might have complex nested structure, but it can be simplified to a list of "data" elems
class XGroup(val name: String) {
  val elms = mutableListOf<XElm>()
}

//does NOT support SUB elements
// is only for "data" elements
class XElm(val name: String) {
  val attrs = mutableMapOf<String, String>()
  var pcdata: String? = null

  //require pcdata
  val rq get() = pcdata ?: error("XElm.rq: $name")

  fun pcdata(data: String) {
    //append instead - may be fragmented  //rarely, but better to handle

    pcdata = (pcdata ?: "") + data
  }
//
//  companion object {
//    const val pcdata = "#PCDATA"
//  }
}