package com.github.maartyl.gdb

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule

//represents ID of a Node in Graph
// - must be "serializable"
interface GId

typealias NodeBase = Any

//represents a TYPED ID of a Node with value T
// T is just a HINT - can be unsafeCast to anything, and will still work, until someone tries to use derefed value
@Serializable(with = GRefCtxSerializer::class)
interface GRef<T : NodeBase> : GId

interface GIndex<TKey : Any, TN : NodeBase> {
  //name of this index
  val name: String

  fun find(snap: GDbSnap, key: TKey): Sequence<GRef<TN>>
}

interface GRangeIndex<TKey : Any, TN : NodeBase> : GIndex<TKey, TN> {

  //values in rslt can REPEAT if it returned multiple keys
  // ... hmm... is that an issue?
//  fun findRange(
//    snap: GDbSnap,
//    //null -> unconstrained
//    startKey: TKey?,
//    endKey: TKey?,
//    startInclusive: Boolean = true,
//    endInclusive: Boolean = true
//  ): Sequence<GRef<TN>>
}

//
//interface GUniqueIndex<TKey, TN : NodeBase> : GIndex<TKey, TN> {
//  //id obtained by fn(TN) ?
//  fun insertOrThrow(tx: GDbTx, node: TN): GRef<TN>
//  fun insertOrReplace(tx: GDbTx, node: TN): GRef<TN>
//  fun insertOrOld(tx: GDbTx, node: TN): GRef<TN>
//  fun find1(snap: GDbSnap, key: TKey): GRef<TN>?
//
//  //derives Ref, whether inserted or not
//  //TODO: is this possible? (if ref just stores key, then yes, otherwise ... ?)
//  //fun deriveRef(key: TKey): GRef<TN>
//}

// is UniqueIndex, but skipping dealing with it for now
interface GPrimaryStrIndex<TN : NodeBase> : GIndex<String, TN> {

  //derives Ref, whether inserted or not
  fun deriveRef(key: String): GRef<TN>

  //TODO: maybe do not return if not in DB ?
  override fun find(snap: GDbSnap, key: String): Sequence<GRef<TN>> {
    return sequenceOf(deriveRef(key))
  }

  fun primaryKeyOf(node: TN): String
//
//  override fun find1(snap: GDbSnap, key: String): GRef<TN>? {
//    return deriveRef(key)
//  }
}

fun <TN : NodeBase> GDbTx.put(idx: GPrimaryStrIndex<TN>, node: TN): GRef<TN> {
  //acts as insertOrReplace
  return idx.deriveRef(idx.primaryKeyOf(node)).also { put(it, node) }
}


@Suppress("UNCHECKED_CAST")
inline fun <reified TL : NodeBase, TR : Any> GDbBuilder.makeIndexStr(
  name: String, noinline seri: (TR) -> String,
  crossinline view: (GRef<TL>, TL, MutableCollection<TR>) -> Unit?,
): GRangeIndex<TR, TL> = makeIndexRawStr<TR>(name, seri) { r, v, c ->
  (v as? TL)?.let { view(r as GRef<TL>, it, c) }
} as GRangeIndex<TR, TL>

@Suppress("UNCHECKED_CAST")
inline fun <reified TL : NodeBase, TR : Any> GDbBuilder.makeIndexLong(
  name: String, noinline seri: (TR) -> Long,
  crossinline view: (GRef<TL>, TL, MutableCollection<TR>) -> Unit?,
): GRangeIndex<TR, TL> = makeIndexRawLong<TR>(name, seri) { r, v, c ->
  (v as? TL)?.let { view(r as GRef<TL>, it, c) }
} as GRangeIndex<TR, TL>

@Suppress("UNCHECKED_CAST")
inline fun <reified TL : NodeBase, TR : NodeBase> GDbBuilder.makeReverseIndex(
  name: String,
  crossinline view: (GRef<TL>, TL, MutableCollection<GRef<TR>>) -> Unit?,
): GIndex<GRef<TR>, TL> = makeIndexRawGRef<TR>(name) { r, v, c ->
  (v as? TL)?.let { view(r as GRef<TL>, it, c) }
} as GIndex<GRef<TR>, TL>


//@Suppress("UNCHECKED_CAST")
//inline fun <TG : Any, reified TN : NodeBase> GDbBuilder.groupIndex(
//  name: String, seri: KSerializer<TG>,
//  crossinline view: (GRef<TN>, TN) -> TG?,
//): GIndex<TG, TN> =
//  groupIndexRaw<TG>(name, seri) { r, v ->
//    (v as? TN)?.let { view(r as GRef<TN>, it) }
//  } as GIndex<TG, TN>


interface GDbBuilder {
  suspend fun build(): GDb


  //needed for Indexes that want to index refs
  fun <T : NodeBase> grefSerializer(): KSerializer<GRef<T>>

  //name must be unique among all indexes in GDb
  // view defines relationship; view is run for ALL nodes in db, after each CHANGE of that node (including add)
  // index.find(TR) returns all TL that returned the TR
//  fun <TR> reverseIndexRaw(
//    name: String,
//    seri: KSerializer<TR>,
//    //ret null == this ref is never indexed (not just empty in this case)
//    //if returns null, must not add to coll
//    view: (GRef<*>, NodeBase, MutableCollection<TR>) -> Unit?,
//  ): GIndex<TR, *>

  //TODO: change these to ENSURE index, and also do somehow ensure it fully reflects latest state...?

  fun <TR : Any> makeIndexRawStr(
    name: String,
    seri: (TR) -> String,
    //ret null == this ref is never indexed (not just empty in this case)
    //if returns null, must not add to coll
    view: (GRef<*>, NodeBase, MutableCollection<TR>) -> Unit?,
  ): GRangeIndex<TR, *>

  fun <TR : Any> makeIndexRawLong(
    name: String,
    seri: (TR) -> Long,
    //ret null == this ref is never indexed (not just empty in this case)
    //if returns null, must not add to coll
    view: (GRef<*>, NodeBase, MutableCollection<TR>) -> Unit?,
  ): GRangeIndex<TR, *>

  fun <TR : NodeBase> makeIndexRawGRef(
    name: String,
    //ret null == this ref is never indexed (not just empty in this case)
    //if returns null, must not add to coll
    view: (GRef<*>, NodeBase, MutableCollection<GRef<TR>>) -> Unit?,
  ): GIndex<GRef<TR>, *>


  //for nodes with externally defined PK
  //TG needs well defined equality
  // if returns null, will NOT be part of any group
  // TId probably must be String or something... - maybe change to only String nad Int and ..? impl... ?
  // - OR: if STR - can be used directly; if weird: is a special table...
  // will need more args, including reified type, etc.
//  fun <TId : Any, TN : NodeBase> uniqueIndex(
//    name: String,
//    seri: KSerializer<TId>,
//    id: (TN) -> TId,
//  ): GIndex<TId, TN>


  //MOST IMPORTANT - allows INSERTING ids that are not auto-generated
  fun <TN : NodeBase> primaryIndex(
    name: String,
    id: (TN) -> String,
  ): GPrimaryStrIndex<TN>


  //TODO: maybe secondary fn: FILTER (synchronous) - if returns false: trigger will not be enqueued
  // + inline extension method, that puts TYPE check in filter
  //btw.: if trigger makes changes, those can in turn cause MORE triggers to be enqueued
  // - potentially infinite??
  //all triggers run queued at the END of a TX (but if update fails, TX fails)
  fun addTriggerRaw(
    //run for each TN that changes
    trigger: suspend GDbTx.(GRef<NodeBase>) -> Unit,
  )
}

//represents a GRAPH of nodes
interface GDb {

//  //UNNECESARY: probably quite pointless ... normal trigger is better
//  //all updates run queued at the END of a TX (but if update fails, TX fails)
//  fun <T1 : NodeBase, T2 : NodeBase> addTriggerUpdate(
//    //run for each T1 that changes
//    // - returns all refs that should be updated in response
//    trigger: GDbSnap.(GRef<T1>, T1) -> Iterable<GRef<T2>>,
//    //updates the T2
//    //TOUP: is it ok to pass in TX ? ... I think it shouldnt update anything else...
//    update: GDbSnap.(GRef<T1>, T1, GRef<T2>, T2?) -> T2?
//  ) = addTrigger<T1> { r1 ->
//    deref(r1)?.let { trigger(r1, it).forEach { r2 -> put(r2, this.update(r1, it, r2, deref(r2))) } }
//  }


  //GDbSnap can only be used inside the block
  suspend fun <T> read(block: suspend GDbSnap.() -> T): T

  //GDbTx can only be used inside the block
  suspend fun <T> mutate(block: suspend GDbTx.() -> T): T

  //emits on every change + first time
  //emits null if not in DB
  fun <T : NodeBase> subscription(ref: GRef<T>): Flow<T?>

  //if any of refs changes, recomputes and emits
  //ALSO: tracks all deferred refs from last invoke of block, and if any of THOSE change: also reruns
  fun <T> subscription(block: suspend GDbSnap.() -> T): Flow<T>
}

//allows for consistent reads from a snapshot of graph
interface GDbSnap {

  //null if not in graph
  fun <T : NodeBase> deref(ref: GRef<T>): T?
}

interface GDbTx : GDbSnap {
  //creates a node with NEW ID
  fun <T : NodeBase> insertNew(node: T): GRef<T>

  //MAYBE: better name
  //inserts or updates or removes
  fun <T : NodeBase> put(ref: GRef<T>, node: T?)
}


@OptIn(ExperimentalSerializationApi::class)
object GRefCtxSerializer : KSerializer<GRef<*>> {

  object PrimitiveSerialDescriptor : SerialDescriptor {
    override val serialName: String = "GRefCtx"
    override val kind = SerialKind.CONTEXTUAL
    override val elementsCount: Int get() = 0
    override fun getElementName(index: Int): String = error()
    override fun getElementIndex(name: String): Int = error()
    override fun isElementOptional(index: Int): Boolean = error()
    override fun getElementDescriptor(index: Int): SerialDescriptor = error()
    override fun getElementAnnotations(index: Int): List<Annotation> = error()
    override fun toString(): String = "PrimitiveDescriptor($serialName)"
    private fun error(): Nothing = throw IllegalStateException("Primitive descriptor does not have elements")
  }

  override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor

  private fun serializer(serializersModule: SerializersModule): KSerializer<GRef<*>> =
    serializersModule.getContextual(GRef::class) ?: error("contextual serializer for GRef not provided")

  override fun serialize(encoder: Encoder, value: GRef<*>) {
    encoder.encodeSerializableValue(serializer(encoder.serializersModule), value)
  }

  override fun deserialize(decoder: Decoder): GRef<*> {
    return decoder.decodeSerializableValue(serializer(decoder.serializersModule))
  }
}