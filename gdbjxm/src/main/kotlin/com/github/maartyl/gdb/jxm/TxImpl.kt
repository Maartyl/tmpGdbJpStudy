package com.github.maartyl.gdb.jxm

import com.github.maartyl.gdb.GDbSnap
import com.github.maartyl.gdb.GDbTx
import com.github.maartyl.gdb.GRef
import com.github.maartyl.gdb.NodeBase
import kotlinx.coroutines.*

internal open class SnapImpl(
  val g: GDbImpl,
  //full duration of slot in RwExec - for INTERNAL use
  val scopeSlot: CoroutineScope,
  // if not null: tracks all derefs
  private val seen: MutableSet<Ref<*>>?,
) : GDbSnap {

  override suspend fun <T : NodeBase> GRef<T>.deref(): T? {
    scopeSlot.ensureActive()
    val r = asRef
    seen?.add(r)

    return r.cachedNode ?: g.nodesGetAndCache(r)
  }

}

//TODO: pass in scope: how long usabe for + allows starting stuff, etc...
// - probably should be "inner" scope: commit AFTER the scope completes
// - so secondary "outer" scope, that also includes commit
// - commit needs to wait, until all "inner" children completed
// - it really would be best, if it could be part of coroutineScope inside block...
// -- but any async updates (like reindexing, triggers ...) need to run in that

internal class TxImpl(
  g: GDbImpl,
  scopeSlot: CoroutineScope,
  seen: MutableSet<Ref<*>>?,
  //active as long as mutation is allowed
  private val mutAllowed: CompletableJob = Job(scopeSlot.coroutineContext.job),
  val scopeMut: CoroutineScope = scopeSlot + mutAllowed,
) : SnapImpl(g, scopeSlot, seen), GDbTx {
  private val changes = mutableSetOf<Ref<*>>()

  suspend fun <T> runTx(block: suspend GDbTx.() -> T): T = try {
    g.chngo.txStart(this)
    block().also {
      //TOUP: instead do REQUESTS for passes and LOOP
      // e.g. isPerformIndexesRequested
      // - will be needed for TRIGGERS
      // - ALSO: for now, all triggers will need to run SEQUENTIALLY - not in parallel
      //   - and most likely, will need to run INDEXES between each .....
      //   - that needs some though. - NO TRIGGERS for now. -- probably: each trigger OWN mutate tx...
      g.chngo.txPreCommit(this)
      mutAllowed.complete()
      mutAllowed.join() //wait for parallel mutations
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

  override suspend fun <T : NodeBase> insertNew(node: T): GRef<T> {
    return genRef<T>().also { it.put(node) }
  }

  //  //MAY returns previous value, like map.put would -- may be confusing; not sure yet
//  // - name it swap maybe... putSwap
  override suspend fun <T : NodeBase> GRef<T>.put(node: T?) {
    mutAllowed.ensureActive()
    val r = asRef
    val old = g.nodesPutSwap(r, node)
    r.cacheLatest(node) // - only fully invalidate if ROLLBACK
    changes.add(r)
    doNodeChanged(r, old, node)
  }

  private fun <TN : NodeBase> doNodeChanged(ref: Ref<TN>, old: TN?, new: TN?) {
    g.chngo.onNodeChanged(this, ref, old, new)
  }
}

