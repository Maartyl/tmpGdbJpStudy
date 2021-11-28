package com.github.maartyl.jp_import

import com.github.maartyl.gdb.put
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import org.xml.sax.Attributes
import org.xml.sax.SAXParseException
import org.xml.sax.helpers.DefaultHandler
import javax.xml.parsers.SAXParserFactory
import kotlin.io.path.pathString

@Serializable
sealed class GImporting

//single kanji from kanjidic2
@Serializable
data class Kanjidic2(
  //contains single unicode character with the kanji
  // - can be 2 long: UTF16 surrogate pair
  val lit: String,
  //1-214
  val radicalNo: Int, //Byte would be enough, but ... would create hassle
  //not needed: can be derived from lit
  //val unicode: Int,


  val freq: Int, //or -1
  val grade: Int, //or -1

  //not defined for all (but almost)
  val strokeCount: Int,  //or -1

  val ids: Set<String>,
  val variants: Set<String>,

  //this kanji is ITSELF a NAMED RADICAL
  val radNames: List<String>,

  //these are effectively SETS but lists are cheaper...
  val meanings: List<String>,
  //TODO: do I need separation, or is kana-type enough? - "containsKatakana->on"
  val readingsKun: List<String>,
  val readingsOn: List<String>,


  ) : GImporting()


class Kanjidic2SaxHandler(private val out: SendChannel<XGroup>) : DefaultHandler() {

  private companion object C {
    //elements that contain PCDATA and ATTrs
    const val dataElms =
      "literal|cp_value|rad_value|grade|stroke_count|variant|freq|rad_name|jlpt|dic_ref|q_code|reading|meaning|nanori"
    val rgxDataElem = Regex(dataElms)
  }

  var kanji: XGroup? = null
  var recent: XElm? = null

  override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes?) {
    if (qName == "character") {
      kanji = XGroup(qName)
      recent = null
    } else if (rgxDataElem.matches(qName)) {
      recent = XElm(qName).also { e ->
        kanji?.elms?.add(e)
        attributes?.let { a ->
          for (i in 0 until a.length) {
            e.attrs[a.getQName(i)] = a.getValue(i)
          }
        }
      }

    } else {
      recent = null
    }
  }

  override fun characters(ch: CharArray, start: Int, length: Int) {
    recent?.pcdata(String(ch, start, length))
  }

  override fun endElement(uri: String?, localName: String?, qName: String) {
    recent = null
    if (qName == "character")
      kanji?.let { out.trySendBlocking(it).getOrThrow(); kanji = null }
  }

  override fun error(e: SAXParseException?) {
    println(e)
    super.error(e)
  }
}

//will CLOSE chan, after done
fun parseKanjidic2Blocking(pathXmlFile: String, out: SendChannel<XGroup>) {
  val fsax = SAXParserFactory.newInstance()
  val sp = fsax.newSAXParser()
  val h = Kanjidic2SaxHandler(out)

  sp.parse(pathXmlFile, h)
  out.close()
}

@OptIn(ExperimentalCoroutinesApi::class)
fun CoroutineScope.parseKanjidic2(pathXmlFile: String): ReceiveChannel<XGroup> {
  return produce(Dispatchers.IO, 200) { parseKanjidic2Blocking(pathXmlFile, this) }.also { ch ->
    //coroutineContext.job.invokeOnCompletion(onCancelling = true) { ch.cancel(it) }
  }
//  return Channel<XGroup>(200).also {
//    launch(Dispatchers.IO) {
//      parseKanjidic2Blocking(pathXmlFile, it)
//    }
//  }
}

fun readKanjidic2(pathXmlFile: String): Flow<Kanjidic2> {
  //not parallel - is IO bound anyway (=file reading)
  return flow {
    coroutineScope {
      for (xGroup in parseKanjidic2(pathXmlFile)) {
        try {
          emit(processKanjidic2(xGroup))
        } catch (t: Throwable) {
          println(t)
          throw t
        }

      }
    }
  }
}

private fun <T> MutableSet<T>?.add(t: T): MutableSet<T> {
  return (this ?: mutableSetOf()).also { it.add(t) }
}

private fun <T> MutableList<T>?.add(t: T): MutableList<T> {
  return (this ?: mutableListOf()).also { it.add(t) }
}

fun processKanjidic2(kg: XGroup): Kanjidic2 {

  lateinit var lit: String
  var radiNo: Int = -1

  var freq = -1
  var grade = -1

  var strokeCount = -1 //not defined for all (but almost)

  val ids: MutableSet<String> = mutableSetOf()
  val variants: MutableSet<String> = mutableSetOf()

  //this kanji is ITSELF a NAMED RADICAL
  val radNames: MutableList<String> = mutableListOf()

  //these are effectively SETS but lists are cheaper...
  val meanings: MutableList<String> = mutableListOf()
  //TODO: do I need separation, or is kana-type enough? - "containsKatakana->on"
  val readingsKun: MutableList<String> = mutableListOf()
  val readingsOn: MutableList<String> = mutableListOf()

  for (e in kg.elms) {
    //println("processKanjidic2 ${e.name}")

    when (e.name) {
      //block: simple elem;  AT MOST ONCE
      "literal" -> lit = e.rq
      "freq" -> freq = e.rq.toInt()

      "grade" -> grade = e.rq.toInt()

      "jlpt" -> {} //useless: OLD value - PASS

      //block: some variants at most once
      "rad_value" -> {
        //ignore nelson_c - not interested for now
        e.attrs.forEach { (k, v) -> if (k == "classical") radiNo = v.toInt() }
      }
      "cp_value" -> {

        for ((an, av) in e.attrs) {
          assert(an == "cp_type")
          when (av) {
            "jis208", "jis212", "jis213" -> {
              ids.add("$av:${e.rq}")
            }
            //ucs == unicode - but not needed
            else -> {}
          }
        }
      }

      //block MIXED repeatability

      "q_code" -> {
        //PASS: for now not interesting
        e.attrs.forEach { (t, u) ->
          assert(t == "qc_type")
          // SINGLE:  deroo, sh_desc
          // MULTI:   four_corner, skip
        }
      }
      //"dic_ref" -> {} //handled later: being unique-key is not useful
      //"variant" -> {} //handled later: being unique-key is only accidental

      //block: REPEATABLE

      "meaning" -> {
        //empty -> english ; otherwise has language attr
        if (e.attrs.isEmpty()) meanings.add(e.rq)
      }
      "reading" -> {
        //always has exactly one attr
        when (e.attrs.keys.first()) {
          "ja_kun" -> readingsKun.add(e.rq)
          "ja_on" -> readingsOn.add(e.rq)
          else -> {} //pass
        }
      }
      "nanori" -> {} //TODO: is this useful for anything ?
      "rad_name" -> radNames.add(e.rq)
      "stroke_count" -> {
        //I only want first - if already known: is a "secondary" count and is wrong
        if (strokeCount == -1) {
          strokeCount = e.rq.toInt()
        } else {
          //curious only
          println("strokeCount: $lit")
        }
      }

      //variant requires:  jis;  deroo, nelson_c, njecd ; oneill, s_h, ucs
      // ! deroo is Q_CODE ... ?? are they unique?
      "variant" -> {
        e.attrs.forEach { (t, u) ->
          assert(t == "var_type")
          val x = if (u == "nelson_c") "nc" else u
          variants.add("$x:${e.rq}")
        }

      } //pass: future
      "dic_ref" -> {
        //pass: future TODO: some needed for variant processing
        e.attrs.forEach { (t, u) ->
          if (t == "dr_type") {
            when (u) {
              "nelson_c" -> ids.add("nc:${e.rq}")
              else -> {
                //TMP
                ids.add("$u:${e.rq}")
              }
            }
          }
          // SINGLE:  deroo, sh_desc
          // MULTI:   four_corner, skip
        }
      }

      else -> error("Unexpected elem: ${e.name}")
    }

  }

  //BAD: shuld replkace with empty read only colls

  //println("built Kanjidic2")
  return Kanjidic2(
    lit = lit,
    radicalNo = radiNo,
    freq = freq,
    grade = grade,
    strokeCount = strokeCount,
    ids = ids,
    variants = variants,
    radNames = radNames,
    meanings = meanings,
    readingsKun = readingsKun,
    readingsOn = readingsOn,
  )
}

// testing ---------------------------------------------

//http://www.edrdg.org/wiki/index.php/KANJIDIC_Project
private val tmpPath = java.nio.file.Path.of(System.getenv("USERPROFILE"), "tmp", "211124_jp", "kanjidic2.xml")

fun inspectKeys(scope: CoroutineScope) {
  val chan = scope.parseKanjidic2(tmpPath.pathString)

  scope.launch {
    val keys = mutableSetOf<String>()
    for (kg in chan) {

      //ignore dic_ref

      for (e in kg.elms) {
        val elm = e.name
        //ignore: lots and trash and ...
//        if (e.name == "dic_ref")
//          continue

        e.attrs.forEach { (k, v) ->
          if (k == "m_page" || k == "m_vol" || k == "skip_misclass")
            return@forEach // SKIP those

          keys.add("<$elm $k=$v/>")
        }
        if (e.attrs.isEmpty()) {
          keys.add("<${elm}/>")
        }

      }
    }

    println(keys.sorted().joinToString("\n"))
  }
}

// elements with MULTIPLE attrs
// ONLY: q_code  qc_type=skip + skip_misclass
//          ... actually: this one is still a key, isn't it? a sub-key but a key still...
//       dic_ref dr_type=moro + (m_page, m_vol)
fun inspectBigElms(scope: CoroutineScope) {
  val chan = scope.parseKanjidic2(tmpPath.pathString)

  scope.launch {
    val keys = mutableSetOf<String>()
    for (kg in chan) {

      //ignore dic_ref

      for (e in kg.elms) {
        val elm = e.name
        //ignore: lots and trash and ...
        if (e.name == "dic_ref")
          continue

        if (e.attrs.size > 1) {
          keys.add(buildString {
            append("<")
            append(e.name)
            append(" ")
            e.attrs.keys.sorted().forEach { k ->
              append("$k=${e.attrs[k]} ")
            }
            append("/>")
          })
        }
      }
    }

    println(keys.sorted().joinToString("\n"))
  }
}


fun inspectFullKeyCounts(scope: CoroutineScope) {
  val chan = scope.parseKanjidic2(tmpPath.pathString)

  scope.launch {
    val rslt = mutableMapOf<String, Int>()
    val foundNotSingle = mutableSetOf<String>()
    val foundSingle = mutableSetOf<String>()
    for (kg in chan) {

      val counts = mutableMapOf<String, Int>()

      for (e in kg.elms) {
        val elm = e.name

        e.attrs.forEach { (k, v) ->
          //not keys
          if (!(k == "m_page" || k == "m_vol" || k == "skip_misclass"))
            counts.merge("<$elm $k=$v/>", 1) { a, b -> a + b }
        }
        if (e.attrs.isEmpty()) {
          counts.merge("<${elm}/>", 1) { a, b -> a + b }
        }
      }

      counts.forEach { (t, u) ->
        rslt.merge("$t * $u", 1) { a, b -> a + b }
        if (u > 1) {
          foundNotSingle.add("$t * 1")
        } else {
          foundSingle.add("$t * 1")
        }
      }

    }

    val alwaysSingle = foundSingle - foundNotSingle
    //val neverSingle = foundNotSingle - foundSingle
    //val restRslt = (rslt.keys - alwaysSingle) - neverSingle

    val restRslt = (rslt.keys - alwaysSingle) - foundNotSingle

    println("ALWAYS SINGLE:")
    alwaysSingle.sorted().forEach { k ->
      println("$k ___ ${rslt[k]}")
    }
    println()

    println("SETS *1:") //only shown counts when was *1, but that is fine - don't want key duplication
    foundNotSingle.sorted().forEach { k ->
      println("$k ___ ${rslt[k]}")
    }
    println()

//    println("NEVER SINGLES:")  //is EMPTY
//    neverSingle.sorted().forEach { k ->
//      println("$k ___ ${rslt[k]}")
//    }
//    println()

    println("REST:")
    restRslt.sorted().forEach { k ->
      println("$k ___ ${rslt[k]}")
    }

  }
}

/*
KEYS specified on ALL:
<cp_value cp_type=ucs/>
<literal/>
<rad_value rad_type=classical/>


FULL KEY COUNTS - ALWAYS SINGLE:
<cp_value cp_type=jis208/> * 1 ___ 6355
<cp_value cp_type=jis212/> * 1 ___ 5801
<cp_value cp_type=jis213/> * 1 ___ 3695
<cp_value cp_type=ucs/> * 1 ___ 13108
<dic_ref dr_type=busy_people/> * 1 ___ 358
<dic_ref dr_type=crowley/> * 1 ___ 500
<dic_ref dr_type=gakken/> * 1 ___ 2052
<dic_ref dr_type=halpern_kkld/> * 1 ___ 2230
<dic_ref dr_type=halpern_kkld_2ed/> * 1 ___ 2904
<dic_ref dr_type=halpern_njecd/> * 1 ___ 3002
<dic_ref dr_type=heisig/> * 1 ___ 3007
<dic_ref dr_type=heisig6/> * 1 ___ 3000
<dic_ref dr_type=henshall/> * 1 ___ 1945
<dic_ref dr_type=henshall3/> * 1 ___ 1006
<dic_ref dr_type=jf_cards/> * 1 ___ 1926
<dic_ref dr_type=kanji_in_context/> * 1 ___ 1947
<dic_ref dr_type=kodansha_compact/> * 1 ___ 1945
<dic_ref dr_type=maniette/> * 1 ___ 2064
<dic_ref dr_type=moro/> * 1 ___ 12438
<dic_ref dr_type=nelson_c/> * 1 ___ 5181
<dic_ref dr_type=oneill_kk/> * 1 ___ 1994
<dic_ref dr_type=sakade/> * 1 ___ 881
<dic_ref dr_type=sh_kk/> * 1 ___ 2229
<dic_ref dr_type=tutt_cards/> * 1 ___ 1945
<freq/> * 1 ___ 2501
<grade/> * 1 ___ 2998
<jlpt/> * 1 ___ 2230
<literal/> * 1 ___ 13108
<q_code qc_type=deroo/> * 1 ___ 2057
<q_code qc_type=sh_desc/> * 1 ___ 6508
<rad_value rad_type=classical/> * 1 ___ 13108
<rad_value rad_type=nelson_c/> * 1 ___ 723
<variant var_type=jis213/> * 1 ___ 1
<variant var_type=oneill/> * 1 ___ 13
<variant var_type=s_h/> * 1 ___ 1
<variant var_type=ucs/> * 1 ___ 97

SETS *1: // *1 always exists, and limiting prevents key duplication here
<dic_ref dr_type=halpern_kkd/> * 1 ___ 3806
<dic_ref dr_type=nelson_n/> * 1 ___ 6728
<dic_ref dr_type=oneill_names/> * 1 ___ 2738
<dic_ref dr_type=sh_kk2/> * 1 ___ 2139
<meaning m_lang=es/> * 1 ___ 266
<meaning m_lang=fr/> * 1 ___ 235
<meaning m_lang=pt/> * 1 ___ 219
<meaning/> * 1 ___ 3784
<nanori/> * 1 ___ 616
<q_code qc_type=four_corner/> * 1 ___ 6061
<q_code qc_type=skip/> * 1 ___ 12276
<rad_name/> * 1 ___ 77
<reading r_type=ja_kun/> * 1 ___ 6276
<reading r_type=ja_on/> * 1 ___ 6184
<reading r_type=korean_h/> * 1 ___ 5552
<reading r_type=korean_r/> * 1 ___ 7652
<reading r_type=pinyin/> * 1 ___ 10911
<reading r_type=vietnam/> * 1 ___ 6942
<stroke_count/> * 1 ___ 12586
<variant var_type=deroo/> * 1 ___ 44
<variant var_type=jis208/> * 1 ___ 1722
<variant var_type=jis212/> * 1 ___ 1008
<variant var_type=nelson_c/> * 1 ___ 864
<variant var_type=njecd/> * 1 ___ 13

*
* */


fun inspectVariants(scope: CoroutineScope) {

}


/*

all static KEYS  for each kanji in Kanjidic2
- for each kanji some can REPEAT or be MISSING
- but otherwise: these "the things you can ask about a kanji"

+ special attrs:
 <q_code  qc_type=skip> + skip_misclass
 <dic_ref dr_type=moro> + (m_page, m_vol)
 - These are NOT keys - they have real values
 - They are "metadata" for the value, rather than a "key"

<cp_value cp_type=jis208/>
<cp_value cp_type=jis212/>
<cp_value cp_type=jis213/>
<cp_value cp_type=ucs/>
<freq/>
<grade/>
<jlpt/>
<literal/>
<meaning m_lang=es/>
<meaning m_lang=fr/>
<meaning m_lang=pt/>
<meaning/>
<nanori/>
<q_code qc_type=deroo/>
<q_code qc_type=four_corner/>
<q_code qc_type=sh_desc/>
<q_code qc_type=skip/>
<rad_name/>
<rad_value rad_type=classical/>
<rad_value rad_type=nelson_c/>
<reading r_type=ja_kun/>
<reading r_type=ja_on/>
<reading r_type=korean_h/>
<reading r_type=korean_r/>
<reading r_type=pinyin/>
<reading r_type=vietnam/>
<stroke_count/>
<variant var_type=deroo/>
<variant var_type=jis208/>
<variant var_type=jis212/>
<variant var_type=jis213/>
<variant var_type=nelson_c/>
<variant var_type=njecd/>
<variant var_type=oneill/>
<variant var_type=s_h/>
<variant var_type=ucs/>
<dic_ref dr_type=busy_people/>
<dic_ref dr_type=crowley/>
<dic_ref dr_type=gakken/>
<dic_ref dr_type=halpern_kkd/>
<dic_ref dr_type=halpern_kkld/>
<dic_ref dr_type=halpern_kkld_2ed/>
<dic_ref dr_type=halpern_njecd/>
<dic_ref dr_type=heisig/>
<dic_ref dr_type=heisig6/>
<dic_ref dr_type=henshall/>
<dic_ref dr_type=henshall3/>
<dic_ref dr_type=jf_cards/>
<dic_ref dr_type=kanji_in_context/>
<dic_ref dr_type=kodansha_compact/>
<dic_ref dr_type=maniette/>
<dic_ref dr_type=moro/>
<dic_ref dr_type=nelson_c/>
<dic_ref dr_type=nelson_n/>
<dic_ref dr_type=oneill_kk/>
<dic_ref dr_type=oneill_names/>
<dic_ref dr_type=sakade/>
<dic_ref dr_type=sh_kk/>
<dic_ref dr_type=sh_kk2/>
<dic_ref dr_type=tutt_cards/>

* */


suspend fun GdbImporting.findVariants() = withContext(Dispatchers.Default) {

  println("findVariants")

  mutate {
    println("mut-start:")
    readKanjidic2(tmpPath.pathString).collect {
      put(kdic2.prim, it)
      println("mut-put: ${it.lit}")
    }
  }

  println("mut-done")

  mutate {
    for (knRef in kdic2.hasVariants.find(this, Unit)) {
      val kn = deref(knRef)

      if (kn == null) {
        println("kn-null: $knRef")
      } else

        launch {
          val all = kn.variants.flatMap { vid ->
            kdic2.ids.find(this@mutate, vid).mapNotNull { vvRef ->
              val vv = deref(vvRef)
              if (vv == null) {
                println("vv-null: $vvRef under ${kn.lit}")
              }
              vv
            }.map { "${it.lit} (${vid})" }
          }

          if (all.isNotEmpty())

            println("vari ${kn.lit} : ${all.joinToString(" ")} ")
        }
    }
  }
}