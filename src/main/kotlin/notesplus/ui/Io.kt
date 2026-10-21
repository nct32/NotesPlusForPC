package notesplus.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext

/** All database work of an Instance runs on this one background thread, in order. */
@OptIn(ExperimentalCoroutinesApi::class)
private val dbThread = Dispatchers.IO.limitedParallelism(1)

suspend fun <T> io(block: () -> T): T = withContext(dbThread) { block() }
