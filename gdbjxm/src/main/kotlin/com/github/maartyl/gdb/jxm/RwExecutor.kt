package com.github.maartyl.gdb.jxm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

interface RwSlot {
  val readOnly: Boolean

  //must not throw
  // must be OK to run multiple times!
  //- it SUSPENDS UNTIL DONE running, but always represents the same run
  suspend fun startAndJoin()
}

//executes SLOTS
// - a WRITE slot only ever executes alone
// - multiple READ-ONLY slots can execute at the same time
class RwExecutor(scope: CoroutineScope) {

  //private val queue = ArrayDeque<RwSlot>()
  private val queue = Channel<RwSlot>(100)

  suspend fun enqueue(slot: RwSlot) {
    queue.send(slot)
  }

  //for now a simple variant
  //future: also run CHEAP RO even if some RW in queue

//  private val mgr = scope.launch {
//    while (true) {
//      val tx = coroutineScope {
//        for (s in queue) {
//          if (!s.readOnly)
//            return@coroutineScope s
//          else
//          //like this: if readOnly can start many in parallel
//            launch { s.startAndJoin() }
//        }
//        null
//      }
//
//      //always only one at a time
//      tx?.startAndJoin()
//
//    }
//  }

  //for now a simple variant
  //future: also run CHEAP RO even if some RW in queue
  //not sure if this one works - lets try it
  private val mgr2 = scope.launch {

    //maybe not even needed? just wait for ALL MY CHILDREN each time?
    //var readOnlys = CompletableDeferred<Unit>(currentCoroutineContext().job)

    val myJob = currentCoroutineContext().job
    for (s in queue) {
      if (s.readOnly) { //those can run in parallel
        launch { s.startAndJoin() }
      } else {
        //this could be replaced with "joinAllChildren" ... but I probably want to purge them anyway
        //- but maybe they are removed automatically, once completed? should be...
        //TODO: remove dbg
        println("RwExecutor.childWaitCount=${myJob.children.count()}")
        myJob.children.forEach { it.join() }
        //readOnlys.await() //wait for readOnlys so far to finish
        //readOnlys = CompletableDeferred<Unit>(currentCoroutineContext().job) //clear for next batch
        s.startAndJoin() //run rw sequentially
      }
    }
  }


}