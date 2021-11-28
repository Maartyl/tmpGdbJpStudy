package com.github.maartyl.jpmodel

import com.github.maartyl.gdb.GRef

//base interface for all nodes - Jp learning
sealed interface JlNode

//not everyone that implements this is part of a kanji
//but those who do NOT implement this CANNOT be
//is part, if defined by RELATION
sealed interface PartOfKanji : JlNode

//kanji or concept?
sealed interface PartOfWord : JlNode

data class JpRadical(
  val text: String,
) : PartOfKanji

data class JpKanji(
  val text: String,
  val parts: List<GRef<PartOfKanji>>,
) : PartOfKanji, PartOfWord

data class JpConcept(
  val meaning: String,

  //should have one main writing, and then alternative writings ?
)


data class JpKanjiConcept(
  val meaning: String,

  //should have one main writing, and then alternative writings ?
)

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
  val radi: GRef<JpRadical>
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

// PartOfWord
//a meaning of a kanji, or kanji+kana suffix
// or maybe even a "general" pure kana word (used in other words)
data class LeConcept(
  override val leProgress: LeProgress,
) : ILe