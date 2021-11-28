package com.github.maartyl.gdb.jxm

import com.github.maartyl.gdb.GDbSnap
import com.github.maartyl.gdb.GRangeIndex
import com.github.maartyl.gdb.GRef
import com.github.maartyl.gdb.NodeBase
import org.mapdb.Serializer
import org.mapdb.serializer.SerializerArrayTuple

//private val seriTupleBytesStr = SerializerArrayTuple(Serializer.BYTE_ARRAY, Serializer.STRING)
private val seriTupleStrStr = SerializerArrayTuple(Serializer.STRING, Serializer.STRING)
private val seriTupleLongStr = SerializerArrayTuple(Serializer.LONG, Serializer.STRING)


internal fun <Key : Any, Node : NodeBase> multiIndexStr(
  g: GDbImpl,
  name: String,
  view: (GRef<*>, NodeBase, MutableCollection<Key>) -> Unit?,
  seri: (Key) -> String,
): MultiIndex<Key, String, Node> = MultiIndex(g, name, view, seri, seriTupleStrStr)

internal fun <Key : Any, Node : NodeBase> multiIndexLong(
  g: GDbImpl,
  name: String,
  view: (GRef<*>, NodeBase, MutableCollection<Key>) -> Unit?,
  seri: (Key) -> Long,
): MultiIndex<Key, Long, Node> = MultiIndex(g, name, view, seri, seriTupleLongStr)

//todo MultiMap - reverse and group indexes, possibly more
internal class MultiIndex<Key : Any, KeyB : Any, Node : NodeBase>(
  val g: GDbImpl,
  override val name: String,
  val view: (GRef<*>, NodeBase, MutableCollection<Key>) -> Unit?,
  val seri: (Key) -> KeyB,
  tupleSerializer: SerializerArrayTuple,
) : ChangeObserver, GRangeIndex<Key, Node> {

  //TODO: delta packing tuples, since key can repeat many times ?
  // - probably not that many times - not worth the slow-down for now ...?
  //seriTupleBytesStr
  private val multimap = g.rw.treeSet("${C.PX_IDX}$name", tupleSerializer)
    //.counterEnable()
    .createOrOpen()

  //TODO: better, maybe even support using STRING instead of byte?
  // - maybe somehow custom sortable? - share impl for orderedIndex ?
  //@OptIn(ExperimentalSerializationApi::class)
  //private fun bs(k: Key) = g.proto.encodeToByteArray(seri, k)
  private fun bs(k: Key) = seri(k)

  private fun pack(k: Key, ref: Ref<*>) = arrayOf(bs(k), ref.id)

  private val txToAdd = mutableSetOf<Array<Any>>()
  private val txToRemove = mutableSetOf<Array<Any>>()
  override fun txPreCommit(tx: TxImpl) {
    multimap.addAll(txToAdd)
    multimap.removeAll(txToRemove)
    txToAdd.clear()
    txToRemove.clear()
  }

  private fun add(k: Key, ref: Ref<*>) {
    val p = pack(k, ref)
    txToRemove.remove(p) //if previous change in this tx was removing it - cancel
    txToAdd.add(p)
  }

  private fun remove(k: Key, ref: Ref<*>) {
    val p = pack(k, ref)
    txToAdd.remove(p) //if previous change in this tx was adding it - cancel
    txToRemove.add(p)
  }

  override fun <TN : NodeBase> onNodeChanged(tx: TxImpl, ref: Ref<TN>, old: TN?, new: TN?) {
    //TODO: am I allowed to call equals ?
    // this check also (mainly) gets rid of BOTH null
    if (old === new) return

    val oldFwd = mutableSetOf<Key>()
    val newFwd = mutableSetOf<Key>()

    //if they MAY ever contribute to index
    var oldMay = true
    var newMay = true

    old?.let { oldMay = null != view(ref, it, oldFwd) }
    new?.let { newMay = null != view(ref, it, newFwd) }

    //conditions where no indexing done:
    // - if ANY node requires false for the same ref, then NONE are required to be indexed
    if (!oldMay || !newMay) return

    if (oldFwd.isNotEmpty())
      for (ok in oldFwd) {
        if (ok !in newFwd)
          remove(ok, ref)
      }
    if (newFwd.isNotEmpty())
      for (nk in newFwd) {
        if (nk !in oldFwd)
          add(nk, ref)
      }
  }

  //TODO: notify INDEX key CHANGE to g -- for SUBSCRIBE
  // onIndexChanged(name, key)
  // MAYBE - also jsut notify: index used with the same onIndexUsed(name, key)
  // - ideally serialized key, so they are easier to compare, without worrying


  // --------------

  override fun find(snap: GDbSnap, key: Key): Sequence<GRef<Node>> {
    val b = bs(key)
    val matchSet = multimap.subSet(arrayOf<Any?>(b), arrayOf<Any?>(b, null))
    return matchSet.asSequence().map { g.internRef(it[1] as String) }
  }

}