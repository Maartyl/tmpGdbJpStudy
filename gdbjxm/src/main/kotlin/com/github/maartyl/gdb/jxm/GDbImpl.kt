package com.github.maartyl.gdb.jxm

import com.github.maartyl.gdb.*
import com.google.common.collect.MapMaker
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlinx.serialization.protobuf.ProtoBuf
import org.mapdb.DB
import org.mapdb.Serializer
import kotlin.coroutines.CoroutineContext


//nodes must be Serializable
fun <T : NodeBase> gdbJxmOpen(
  //assumes NOT IO Dispatcher - is for maintanance around
  //must have JOB - Once Completes - CLOSES rw
  scope: CoroutineScope,
  nodeSeri: KSerializer<T>,
  //ro: DB,
  //ReadWrite backing DB
  rw: DB,
  //for running queries
  ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
): GDbBuilder {
  // TODO ... hmm, the Transactions are quite confusing ...
  //  - if whole DB is a single transaction... do I need another for read only stuff?
  //  - would they share mem, or completely separate, only same file??
  // or maybe use my own, single-thread executor ?
  // - but MapDB claims to be thread-safe, so ... it is, but not transactions???
  val j = Job(scope.coroutineContext.job)
  val impl = GDbImpl(coroutineContext = scope.coroutineContext + j, rw, nodeSeri, ioDispatcher)
  scope.launch {
    //probably must wait until completed, not just awaitCancellation()
    //rw.use { awaitCancellation() }
    rw.use { j.join() }
  }
  return impl
}

//@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
//@OptIn(ExperimentalSerializationApi::class)
//OOH! nice! This is not needed at all. - all is solved by polymorphic thingy - great
//@Serializable(with = RefCtxSerializer::class)
class Ref<T : NodeBase>(
  //format: type(args)
  //- for now, args interpretation depends on type, BUT: cannot contain parens EXCEPT as grouping of subkeys
  // - comma should be used to separate multiple args, but only interpreted by the specific type
  // - TODO: replace copmma with * - continuous block of ASCII; also even less used probably
  // - for encoded escaped strs use:  ( ) * -> *L *R *S respectively
  // -- unless it also needs multiple args, in which case... idk: a 4th special char just for escaping?
  // -- how about ASCII-ESC for escaping? that's a good idea!
  // -- and maybe even for parens? who's gonna look at it?! - it's just for internal stuff
  //    and almost all things can handle arbitrary unicode...
  //    - and escaping will almost never be needed...
  // ... although: ids that are easy for user to use are nice too,
  //    especially if I want some text UI, clipboard copying, ...
  //i.e. it is possible:  edge(_g(a3),idx(hello))

  //TODO! NEW IDEA! - better!?
  // - will NEVER be parsed, but can be safely derived - always the same
  // - format:
  //   #(<sha1>)           //keys TOO LONG (longer than sha1+3)
  //     hmm... this saves space, but prevents knowing node type from ID ... would parsing ever be worth it?
  //   %(<gen-str>)        //generated keys
  //   indexName(pkValue)     // keys from "primary" indexes  or idIndex or... - no way to iterate...
  //example edge:  +(type*%(left)*%(right))
  // ... WAIT: cannot edge type be just part of name???
  //  +edgeType(%(left)*%(right))
  // SEPARATOR? if multi-args can only be other ids: separator is not needed: parens are enough
  val id: String,
) : GRef<T> {

  override fun hashCode(): Int {
    return id.hashCode()
  }

  override fun equals(other: Any?): Boolean {
    val o = other as? Ref<*> ?: return false
    return id == o.id
  }

  override fun toString(): String {
    return "Ref($id)"
  }

  //if null: reading this node reads from db

  var cachedNode: T? = null

  //invoked when ref node gets changed value
  fun invalidate() {
    cachedNode = null
  }

  fun updated(node: T?) {
    cachedNode = node
  }
}

internal val <T : NodeBase> GRef<T>.asRef get() = this as? Ref<T> ?: error("bad Ref $this")

internal interface ChangeObserver {

  fun txStart(tx: TxImpl) {}
  fun txPreCommit(tx: TxImpl) {}

  //actually, nothing may be changed anymore...
  fun txPostCommit(tx: SnapImpl) {}
  fun txPreRollback(tx: TxImpl) {}
  fun txPostRollback(tx: SnapImpl) {}

  fun <TN : NodeBase> onNodeChanged(tx: TxImpl, ref: Ref<TN>, old: TN?, new: TN?)

  //TODO: onIndexChanged(name, key)  --  (for subscribe)

  //fun nodesChanged(tx: TxImpl, ns: List<Ref<*>>)
}

internal object C {


  const val PX_IDX = "index!"
  const val NODES = "graph!nodes"
  const val NODE_ID_GEN = "graph!nodeIdGen"

  //  val refDescriptor = PrimitiveSerialDescriptor(
//    "com.github.maartyl.jxm.RefAsStringSerializer", PrimitiveKind.STRING
//  )
  val refDescriptor = PrimitiveSerialDescriptor(
    "jxm.RS", PrimitiveKind.STRING
  )
}

//@OptIn(ExperimentalSerializationApi::class)
//object RefCtxSerializer : KSerializer<GRef<*>> {
//
//  object PrimitiveSerialDescriptor : SerialDescriptor {
//    override val serialName: String = "com.github.maartyl.jxm.RefCtxSerializer"
//    override val kind = SerialKind.CONTEXTUAL
//    override val elementsCount: Int get() = 0
//    override fun getElementName(index: Int): String = error()
//    override fun getElementIndex(name: String): Int = error()
//    override fun isElementOptional(index: Int): Boolean = error()
//    override fun getElementDescriptor(index: Int): SerialDescriptor = error()
//    override fun getElementAnnotations(index: Int): List<Annotation> = error()
//    override fun toString(): String = "PrimitiveDescriptor($serialName)"
//    private fun error(): Nothing = throw IllegalStateException("Primitive descriptor does not have elements")
//  }
//
//  private fun serializer(serializersModule: SerializersModule): KSerializer<GRef<*>> =
//    serializersModule.getContextual(GRef::class) ?: error("contextual serializer for Ref not provided")
//
//  override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor
//
//  override fun serialize(encoder: Encoder, value: GRef<*>) {
//    encoder.encodeSerializableValue(serializer(encoder.serializersModule), value)
//  }
//
//  override fun deserialize(decoder: Decoder): GRef<*> {
//    return decoder.decodeSerializableValue(serializer(decoder.serializersModule))
//  }
//}


//private class InterningRefSerializer(private val loadRef: (String) -> Ref<*>) : KSerializer<Ref<*>> {
//  override val descriptor: SerialDescriptor get() = C.refDescriptor
//  override fun serialize(encoder: Encoder, value: Ref<*>) = encoder.encodeString(value.id)
//  override fun deserialize(decoder: Decoder): Ref<*> = loadRef(decoder.decodeString())
//}

private class InterningGRefSerializer(private val loadRef: (String) -> Ref<*>) : KSerializer<GRef<*>> {
  override val descriptor: SerialDescriptor get() = C.refDescriptor
  override fun serialize(encoder: Encoder, value: GRef<*>) = encoder.encodeString(value.asRef.id)
  override fun deserialize(decoder: Decoder): GRef<*> = loadRef(decoder.decodeString())
}

internal class GDbImpl(
  override val coroutineContext: CoroutineContext,
  //ro: DB,
  val rw: DB,
  private val nodeSeri: KSerializer<*>,
  val ioDispatcher: CoroutineDispatcher,
) : GDb, GDbBuilder, CoroutineScope {

  private var isBuilt = false
  override suspend fun build(): GDb {
    isBuilt = true
    //TODO: run mem-reindexing
    return this
  }

  private fun checkBuilding() {
    if (isBuilt) error("GDb already built.")
  }

  //val weakRefs = mutableMapOf<Long, WeakReference<Ref<*>>>()
  private val weakRefs = MapMaker().weakValues().makeMap<String, Ref<*>>()

  private fun internRefUntyped(id: String): Ref<*> {
    return internRef<NodeBase>(id)
  }

  @Suppress("UNCHECKED_CAST")
  fun <T : NodeBase> internRef(id: String): Ref<T> {
    return weakRefs.getOrPut(id) { Ref<T>(id) } as Ref<T>
  }

  private val grefSeri = InterningGRefSerializer(this::internRefUntyped)

  @OptIn(ExperimentalSerializationApi::class)
  val proto = ProtoBuf {
    serializersModule = SerializersModule {
      contextual(grefSeri)

      //argh, still not great: this saves name of the serializer with each REF because it's polymorphic
      // - even though it will never be anything else ...
      // dammit, still not perfect
      //polymorphic(GRef::class, Ref::class, grefSeri)
    }
  }

  @Suppress("UNCHECKED_CAST")
  private val nodes = rw.hashMap(C.NODES, Serializer.STRING, Serializer.BYTE_ARRAY)
    .createOrOpen()

  @Suppress("UNCHECKED_CAST")
  fun <T : NodeBase> nodesGet(ref: Ref<T>): T? {
    return nodes[ref.id]?.let {
      @OptIn(ExperimentalSerializationApi::class)
      proto.decodeFromByteArray(nodeSeri, it) as T
    }
  }

  @Suppress("UNCHECKED_CAST")
  fun <T : NodeBase> nodesPut(ref: Ref<T>, node: T?) {
    if (node == null) {
      nodes.remove(ref.id)
    } else {
      @OptIn(ExperimentalSerializationApi::class)
      nodes[ref.id] = proto.encodeToByteArray(nodeSeri as KSerializer<T>, node)
    }
  }

  val nodeIdGen = rw.atomicLong(C.NODE_ID_GEN).createOrOpen()

  val chngo = ChangeDispatcher(this)

  override fun <T : NodeBase> grefSerializer(): KSerializer<GRef<T>> {
    @Suppress("UNCHECKED_CAST")
    return grefSeri as KSerializer<GRef<T>>
  }

  override fun <TR : Any> makeIndexRawStr(
    name: String,
    seri: (TR) -> String,
    view: (GRef<*>, NodeBase, MutableCollection<TR>) -> Unit?
  ): GRangeIndex<TR, *> {
    return multiIndexStr<TR, NodeBase>(this, name, view, seri).also {
      chngo.register(it)
    }
  }

  override fun <TR : Any> makeIndexRawLong(
    name: String,
    seri: (TR) -> Long,
    view: (GRef<*>, NodeBase, MutableCollection<TR>) -> Unit?
  ): GRangeIndex<TR, *> {
    return multiIndexLong<TR, NodeBase>(this, name, view, seri).also {
      chngo.register(it)
    }
  }

  override fun <TR : NodeBase> makeIndexRawGRef(
    name: String,
    view: (GRef<*>, NodeBase, MutableCollection<GRef<TR>>) -> Unit?
  ): GIndex<GRef<TR>, *> = makeIndexRawStr(name, { it.asRef.id }, view)

  override fun <TN : NodeBase> primaryIndex(name: String, id: (TN) -> String): GPrimaryStrIndex<TN> {
    return PrimaryStrIndexImpl(this, name, id)
  }

  override fun addTriggerRaw(trigger: suspend GDbTx.(GRef<NodeBase>) -> Unit) {
    TODO("Not yet implemented")
  }

  //TOUP: maybe pass in Dispatchers.Unconstrained ? - it will only ever do cheap synchronization stuff
  private val executor: RwExecutor = RwExecutor(this)

  private suspend fun <T> execEnqueue(readOnly: Boolean, block: suspend CoroutineScope.() -> T): T {
    return coroutineScope {
      //I need exceptions to be propagated only here + correct context...
      val work = async(ioDispatcher, start = CoroutineStart.LAZY, block)

      val h = this@GDbImpl.coroutineContext.job.invokeOnCompletion {
        work.cancel(CancellationException("GDb cancelled and closed", it))
      }

      executor.enqueue(object : RwSlot {
        override val readOnly = readOnly
        override suspend fun startAndJoin() {
          work.join()
          h.dispose()
        }
      })

      //SADLY!! this STARTS it - need another signal to start the block
      //work.await()
      work
      //AHA! - done like this: the coroutineScope cannot return until all children completed
      // - so work must run first, even though it was already returned
      // - the await cannot trigger start of work
    }.await()
  }

  override suspend fun <T> read(block: suspend GDbSnap.() -> T): T = execEnqueue(readOnly = true) {
    SnapImpl(this@GDbImpl, null).block()
  }

  override suspend fun <T> mutate(block: suspend GDbTx.() -> T): T = execEnqueue(readOnly = false) {
    TxImpl(this@GDbImpl, null).runTx(block)
  }

  //cannot be SharedFlow
  //TODO: how to "throw" if scope ends etc?
  // I guess it would be best to take SCOPE as arg? ...or return just Flow
  // - but then they couldn't return the same inst for all... probably fine, though?
  //  - after all: this is chpep, and it's not possible for the other anyway
  override fun <T : NodeBase> subscription(ref: GRef<T>): Flow<T?> {
    //TOUP: cheaper implementation - direct ChangeListener - pass new value
    // - WAIT: cannot if TX !! - still must enqueue itself for end of TX, but still faster
    return channelFlow {
      val co = SubscriptionRef(this@GDbImpl, this, ref.asRef)
      chngo.register(co)

      awaitClose {
        chngo.unregister(co)
      }
    }.buffer(1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  }

  //cannot be SharedFlow
  //TODO:DAMMIT: SharedFlow does not forward exceptions?
  // I guess it would be best to take SCOPE as arg? ...or return just Flow
  // ...shared is kind of nonsense here... should just return Flow?
  override fun <T> subscription(block: suspend GDbSnap.() -> T): Flow<T> {

    return flow {
      val obs = object { //TODO: an impl of CO
        val changeSeen = MutableStateFlow(false)

        //starts with none - fine, as it will run anyway
        val toLookOutFor: MutableSet<Ref<*>> = mutableSetOf()
      }
      try {

      } finally {
        //TODO: unregister obs
      }
      //hmm... this is a nice idea, but how to not MISS changes BETEEN registers?
      // - SO long as I RUN - no TX can run - so: gotta register BEFORE I finish
      while (true) {

        //TODO: A!!! FFS ... what if change to INDEX ?? I would not be notified...
        // - all "root" things need to be saved in "seen" - including "checked index for key X"
        // - without this, could now be returning a different set, and I would not notice

        val toEmit = execEnqueue(readOnly = true) {
          obs.changeSeen.value = false
          obs.toLookOutFor.clear()
          SnapImpl(this@GDbImpl, obs.toLookOutFor).block()
        }
        emit(toEmit)

        obs.changeSeen.first { it } //suspend until something seen to have changed
      }


    }
  }

}

internal class ChangeDispatcher(g: GDbImpl) : ChangeObserver {
  //TODO: maybe take snapshot at the start of Tx/Snap ?
  private var handlers = persistentSetOf<ChangeObserver>()

  //TODO: is it OK in the middle of a "tx" ?
  // - probably not: handlers may depend on seeing the whole Tx lifecycle...
  fun register(obs: ChangeObserver) = synchronized(handlers) {
    handlers = handlers.add(obs)
  }

  //TODO: is it OK in the middle of a "tx" ?
  fun unregister(obs: ChangeObserver) = synchronized(handlers) {
    handlers = handlers.remove(obs)
  }


  override fun <TN : NodeBase> onNodeChanged(tx: TxImpl, ref: Ref<TN>, old: TN?, new: TN?) {
    handlers.forEach { it.onNodeChanged(tx, ref, old, new) }
  }

  override fun txStart(tx: TxImpl) {
    handlers.forEach { it.txStart(tx) }
  }

  override fun txPreCommit(tx: TxImpl) {
    handlers.forEach { it.txPreCommit(tx) }
  }

  override fun txPostCommit(tx: SnapImpl) {
    handlers.forEach { it.txPostCommit(tx) }
  }

  override fun txPreRollback(tx: TxImpl) {
    handlers.forEach { it.txPreRollback(tx) }
  }

  override fun txPostRollback(tx: SnapImpl) {
    handlers.forEach { it.txPostRollback(tx) }
  }

}