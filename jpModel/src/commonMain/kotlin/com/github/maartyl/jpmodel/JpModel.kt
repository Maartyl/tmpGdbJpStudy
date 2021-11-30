package com.github.maartyl.jpmodel

import com.github.maartyl.gdb.GRef
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

//base interface for all nodes - Jp learning
sealed interface JlNode

@Serializable
sealed class JLNode : JlNode

//not everyone that implements this is part of a kanji
//but those who do NOT implement this CANNOT be
//is part, if defined by RELATION
sealed interface PartOfKanji : JlNode

//kanji or concept?
sealed interface PartOfWord : JlNode

@Serializable
@SerialName("jpR")
data class RadicalC(
  val text: String,
) : JLNode(), PartOfKanji

@Serializable
@SerialName("waniRadi")
data class RadicalWani(
  val text: String,
) : JLNode(), PartOfKanji

@Serializable
@SerialName("jpK")
data class Kanji(
  val lit: String,
  //TODO: is it ordered? or a set?
  val parts: List<GRef<PartOfKanji>>,
) : JLNode(), PartOfKanji, PartOfWord

//KanjiGlyph ... do I want those?

@Serializable
@SerialName("jpW")
data class Word(
  val text: String,
  val parts: List<GRef<PartOfKanji>>,
) : JLNode(), PartOfWord
//TODO: is Word a PartOfWord ? ... I guess a word can contain another word
// - but do I want that?
// - in this case it should NOT be just substring, but really the 'concept'


//TODO: Concepts - too complicated, not useful enough
// - also: different shape than all existing dictionary data

//data class JpConcept(
//  val meaning: String,
//
//  //should have one main writing, and then alternative writings ?
//)
//
//
//data class JpKanjiConcept(
//  val meaning: String,
//
//  //should have one main writing, and then alternative writings ?
//)

//TODO: keep IMPORTANCE for each word - not just for "initial" order
// - also to choose which to study first, if many "waiting"
// - can be derived from how COMMON the word is - or if user enters extra, marking it as more important

// ----
// Learning == Le

interface ILe : JlNode {
  val leProgress: LeProgress
}

//progress on a single learning
data class LeProgress(
  val tmp: String,
)

//also simple phrases
data class LeRadiMeaning(
  override val leProgress: LeProgress,
  val radi: GRef<RadicalC>
) : ILe

//usually: uses CONCEPTS
data class LeKanjiMeaning(
  override val leProgress: LeProgress,
) : ILe

//a way to write on PC - one WORD that is simple to type, completes to this kanji, common...
data class LeKanjiWriting(
  override val leProgress: LeProgress,
) : ILe

//also simple phrases
data class LeWordMeaning(
  override val leProgress: LeProgress,
) : ILe

//also simple phrases
data class LeWordReading(
  override val leProgress: LeProgress,
) : ILe

//// PartOfWord
////a meaning of a kanji, or kanji+kana suffix
//// or maybe even a "general" pure kana word (used in other words)
//data class LeConcept(
//  override val leProgress: LeProgress,
//) : ILe