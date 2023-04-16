package com.keylesspalace.tusky.components.occurrence

import android.util.Log
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.AppDatabase
import kotlinx.coroutines.runBlocking
import java.util.Calendar
import javax.inject.Inject
import kotlin.math.min

class OccurrenceRepository @Inject constructor(private val db: AppDatabase, private val accountManager: AccountManager) {
    private var lastApiCalls = HashMap<Long, OccurrenceEntity>(13)
    private var apiCallsCounter = 0

    private val occurrenceDao = db.occurrenceDao()

    fun loadAll(): List<OccurrenceEntity> {
        val occurrences: List<OccurrenceEntity>
        runBlocking {
            occurrences = occurrenceDao.loadAll()
        }

        return occurrences
    }

    // TODO this could/should also record warning and error logs (from "Log").
    //   However that seems not to be intercept-able? Also see commented code block in MainActivity.onCreate

    fun handleApiCallStart(what: String): Long {

        // TODO The account id here could be wrong (for worker tasks for example)

        val occurrence = OccurrenceEntity(
            accountId = accountManager.activeAccount?.id,
            type = OccurrenceEntity.Type.APICALL,
            what = what,
            startedAt = Calendar.getInstance().time,
            callTrace = ""
//            callTrace = OccurrenceEntity.reduceTrace(Throwable().stackTrace),
        )
        // TODO all stack traces here have no hint where they might have originated (always ThreadPool)
        //   found kotlinx.coroutines.stacktrace.recovery but that should be on by default?
        //  There is also a kotlinx.coroutines.debug.DebugProbes. But that hangs on "install()".

        val entityId: Long
        runBlocking {
            // TODO runBlocking is the right thing to do here?
            entityId = occurrenceDao.insertOrReplace(occurrence)
        }

        lastApiCalls[entityId] = occurrence.copy(id = entityId)

        if (++apiCallsCounter % CLEANUP_INTERVAL == 0) {
            runBlocking {
                occurrenceDao.cleanup(entityId - MAXIMUM_ENTRIES)
            }
        }

        return entityId
    }

    fun handleApiCallFinish(id: Long, responseCode: Int) {
        val startedOccurrence = lastApiCalls[id]

        if (startedOccurrence == null) {
            Log.e(TAG, "Last occurrence entity not found in handleApiCallFinish for $id")

            return
        }

        val occurrence = startedOccurrence.copy(
            finishedAt = Calendar.getInstance().time,
            code = responseCode,
        )

        runBlocking {
            occurrenceDao.insertOrReplace(occurrence)
        }

        lastApiCalls.remove(id)
        // TODO that map can grow (lots of unfinished calls that are never removed)?
    }

    fun handleException(exception: Throwable) {
        var rootCause = exception
        while (rootCause.cause != null && rootCause != rootCause.cause) {
            rootCause = rootCause.cause!!
        }

        val traceString = OccurrenceEntity.reduceTrace(rootCause.stackTrace)
        var what = exception.message
        if (what == null && traceString.isNotEmpty()) {
            what = traceString.substring(0, min(200, traceString.length))
        }

        runBlocking {
            occurrenceDao.insertOrReplace(OccurrenceEntity(
                accountId = accountManager.activeAccount?.id,
                type = OccurrenceEntity.Type.CRASH,
                what = what ?: "CRASH",
                startedAt = Calendar.getInstance().time,
                callTrace = traceString
            ))
        }
    }

    companion object {
        private const val TAG = "OccurrenceRepository"
        private const val CLEANUP_INTERVAL = 5
        private const val MAXIMUM_ENTRIES = 100
    }
}
