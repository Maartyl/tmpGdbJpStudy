package com.github.maartyl.gdb.jxm

import com.github.maartyl.gdb.GDbSnap
import com.github.maartyl.gdb.GDbTx
import com.github.maartyl.gdb.GRef
import com.github.maartyl.gdb.NodeBase

internal open class SnapImpl(
  val g: GDbImpl,
  // if not null: tracks all derefs
  private val seen: MutableSet<Ref<*>>?,
) : GDbSnap {

  override fun <T : NodeBase> deref(ref: GRef<T>): T? {
    val r = ref as? Ref<T> ?: error("bad Ref $ref")
    seen?.add(r)

    return r.cachedNode ?: g.nodesGet(r).also {
      r.cachedNode = it
    }
  }

}

internal class TxImpl(
  g: GDbImpl,
  seen: MutableSet<Ref<*>>?,
) : SnapImpl(g, seen), GDbTx {
  private val changes = mutableSetOf<Ref<*>>()

  suspend fun <T> runTx(block: suspend GDbTx.() -> T): T = try {
    g.chngo.txStart(this)
    block().also {
      g.chngo.txPreCommit(this)
      g.rw.commit()
      //TODO: what if throws here ? - probably should not run Rollback stuff, at least?
      // - MUST NOT throw in postCommit  //fine in preCommit, even expected
      g.chngo.txPostCommit(this)
    }
  } catch (t: Throwable) {
    runCatching { g.chngo.txPreRollback(this) }.onFailure { t.addSuppressed(it) }
    runCatching { g.rw.rollback() }.onFailure { t.addSuppressed(it) }
    changes.forEach { it.invalidate() }
    runCatching { g.chngo.txPostRollback(this) }.onFailure { t.addSuppressed(it) }

    throw t
  }

  private fun <T : NodeBase> genRef(): GRef<T> {
    //see Ref for id contents schema
    return g.internRef("%(${g.nodeIdGen.andIncrement.toString(Character.MAX_RADIX)})")
  }

  override fun <T : NodeBase> insertNew(node: T): GRef<T> {
    return genRef<T>().also { put(it, node) }
  }

  override fun <T : NodeBase> put(ref: GRef<T>, node: T?) {

    //TODO: use single db SWAP (get and set) instead of loading old value, then storing new
    // - (for times, when not already cached inside Ref)
    //TODO: do I actually need old value? - oh, yeah! I do, for indexes.
    // ... actually, that might be kinda bad: if some mistake... would it not better to use real DB state?
    // - not just bad: impossible - the forward direction is not stored - impossible without deriving from node
    val old = deref(ref)
    val r = ref.asRef
    g.nodesPut(r, node)

    r.updated(node)
    // - only fully invalidate if ROLLBACK

    changes.add(r)
    doNodeChanged(r, old, node)
  }

  private fun <TN : NodeBase> doNodeChanged(ref: Ref<TN>, old: TN?, new: TN?) {
    g.chngo.onNodeChanged(this, ref, old, new)
  }
}

