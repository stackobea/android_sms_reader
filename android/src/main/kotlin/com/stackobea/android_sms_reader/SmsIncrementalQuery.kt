package com.stackobea.android_sms_reader

import android.content.ContentResolver
import android.database.Cursor
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.util.concurrent.Executors

/**
 * Incremental inbox query: returns inbox rows strictly AFTER a (date, _id)
 * checkpoint, in deterministic order (date ASC, _id ASC). Filtering happens in
 * the SMS content provider, so old messages never cross into Dart.
 *
 * Notes for maintainers:
 *  - `Telephony.Sms.DATE` is the time the device RECEIVED the message (ms).
 *    `DATE_SENT` is deliberately not used.
 *  - A pure (date, _id) checkpoint cannot see messages inserted with a date
 *    OLDER than the checkpoint (e.g. restored from backup, imported by another
 *    app, large device-clock change). Callers needing those must do a full scan.
 *  - Requires READ_SMS; a missing permission surfaces as PERMISSION_DENIED.
 */
internal object SmsIncrementalQuery {
    /** Hard upper bound per call, regardless of what the caller asks for. */
    private const val MAX_BATCH = 500
    private const val DEFAULT_BATCH = 50

    // Daemon thread: never keeps the process alive, never blocks the UI thread.
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "sms-incremental-query").apply { isDaemon = true }
    }
    private val main = Handler(Looper.getMainLooper())

    private val PROJECTION = arrayOf(
        Telephony.Sms._ID,
        Telephony.Sms.ADDRESS,
        Telephony.Sms.BODY,
        Telephony.Sms.DATE
    )

    // The WHOLE expression is wrapped in parentheses. The inbox URI appends its
    // own `type = 1` filter; without the outer parens, a provider that does not
    // wrap the caller's selection would evaluate `type=1 AND a OR b`.
    private const val SELECTION =
        "((${Telephony.Sms.DATE} > ?) OR (${Telephony.Sms.DATE} = ? AND ${Telephony.Sms._ID} > ?))"

    private const val SORT = "${Telephony.Sms.DATE} ASC, ${Telephony.Sms._ID} ASC"

    private fun query(
        resolver: ContentResolver,
        projection: Array<String>,
        afterDate: Long,
        afterId: Long,
        limit: Int?
    ): Cursor? {
        val args = arrayOf(afterDate.toString(), afterDate.toString(), afterId.toString())
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val b = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, SELECTION)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args)
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, SORT)
                // Only a hint: providers may ignore it. The row loop in
                // fetchAsync enforces the real cap.
                if (limit != null) putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            }
            resolver.query(Telephony.Sms.Inbox.CONTENT_URI, projection, b, null)
        } else {
            val sort = if (limit != null) "$SORT LIMIT $limit" else SORT
            resolver.query(Telephony.Sms.Inbox.CONTENT_URI, projection, SELECTION, args, sort)
        }
    }

    /** Runs on the main thread; swallows "reply already sent / engine detached". */
    private fun post(block: () -> Unit) {
        main.post {
            try {
                block()
            } catch (_: IllegalStateException) {
            }
        }
    }

    fun fetchAsync(resolver: ContentResolver, call: MethodCall, result: MethodChannel.Result) {
        val afterDate = call.argument<Number>("afterDate")?.toLong() ?: 0L
        val afterId = call.argument<Number>("afterId")?.toLong() ?: 0L
        val count = (call.argument<Number>("count")?.toInt() ?: DEFAULT_BATCH)
            .coerceIn(1, MAX_BATCH)
        executor.execute {
            try {
                val out = ArrayList<Map<String, Any?>>(count)
                query(resolver, PROJECTION, afterDate, afterId, count)?.use { c ->
                    val iId = c.getColumnIndexOrThrow(Telephony.Sms._ID)
                    val iAddr = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                    val iBody = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
                    val iDate = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
                    // Hard cap: correct even if the provider ignored the LIMIT hint.
                    while (out.size < count && c.moveToNext()) {
                        out.add(
                            mapOf(
                                "id" to c.getLong(iId),
                                "address" to (c.getString(iAddr) ?: ""),
                                "body" to (c.getString(iBody) ?: ""),
                                "date" to c.getLong(iDate),
                                "type" to "inbox"
                            )
                        )
                    }
                }
                post { result.success(out) } // empty list = nothing new
            } catch (e: SecurityException) {
                post { result.error("PERMISSION_DENIED", e.message, null) }
            } catch (e: Exception) {
                post { result.error("QUERY_FAILED", e.message, null) }
            }
        }
    }

    fun countAsync(resolver: ContentResolver, call: MethodCall, result: MethodChannel.Result) {
        val afterDate = call.argument<Number>("afterDate")?.toLong() ?: 0L
        val afterId = call.argument<Number>("afterId")?.toLong() ?: 0L
        executor.execute {
            try {
                val n = query(resolver, arrayOf(Telephony.Sms._ID), afterDate, afterId, null)
                    ?.use { it.count } ?: 0
                post { result.success(n) }
            } catch (e: SecurityException) {
                post { result.error("PERMISSION_DENIED", e.message, null) }
            } catch (e: Exception) {
                post { result.error("QUERY_FAILED", e.message, null) }
            }
        }
    }
}