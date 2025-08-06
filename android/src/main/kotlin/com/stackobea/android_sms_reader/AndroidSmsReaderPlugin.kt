package com.stackobea.android_sms_reader

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.*

/**
 * Flutter plugin for reading SMS messages on Android.
 *
 * Supports:
 * - Permission check
 * - Fetching messages with pagination and search
 * - Getting message count
 * - Observing new incoming SMS via BroadcastReceiver
 */

class AndroidSmsReaderPlugin : FlutterPlugin, MethodChannel.MethodCallHandler,
    EventChannel.StreamHandler, ActivityAware {

    companion object {
        // Method channel used for calling native SMS methods from Flutter
        private const val METHOD_CHANNEL_NAME = "android_sms_reader"

        // Event channel used for streaming new incoming SMS to Flutter
        private const val EVENT_CHANNEL_NAME = "sms_observer"
    }

    private lateinit var channel: MethodChannel
    private lateinit var eventChannel: EventChannel
    private var context: Context? = null
    private var activity: Activity? = null
    private var smsReceiver: BroadcastReceiver? = null
    private var eventSink: EventChannel.EventSink? = null

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        context = binding.applicationContext

        channel = MethodChannel(binding.binaryMessenger, METHOD_CHANNEL_NAME)
        eventChannel = EventChannel(binding.binaryMessenger, EVENT_CHANNEL_NAME)

        channel.setMethodCallHandler(this)
        eventChannel.setStreamHandler(this)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        val currentContext = context
        if (currentContext == null) {
            result.error("NULL_CONTEXT", "Context is not available", null)
            return
        }

        when (call.method) {
            "requestPermissions" -> {
                // Check if READ_SMS permission is granted
                val hasPermission = ContextCompat.checkSelfPermission(
                    currentContext,
                    Manifest.permission.READ_SMS
                ) == PackageManager.PERMISSION_GRANTED

                result.success(hasPermission)
            }

            "fetchMessages" -> {
                // Fetch paginated SMS messages with optional search query
                val type = call.argument<String>("type")
                val start = call.argument<Int>("start") ?: 0
                val count = call.argument<Int>("count") ?: 50
                val query = call.argument<String>("query")

                if (type.isNullOrEmpty()) {
                    result.error("INVALID_ARGUMENT", "Missing required argument: type", null)
                    return
                }

                result.success(fetchMessages(type, start, count, query))
            }

            "getMessageCount" -> {
                // Get total count of SMS messages of given type
                val type = call.argument<String>("type")
                if (type.isNullOrEmpty()) {
                    result.error("INVALID_ARGUMENT", "Missing required argument: type", null)
                    return
                }

                result.success(getMessageCount(type))
            }

            else -> result.notImplemented()
        }
    }

    private fun fetchMessages(
        type: String,
        start: Int,
        count: Int,
        query: String?
    ): List<Map<String, Any>> {
        val currentContext = context ?: return emptyList()

        // Use appropriate SMS URI based on type ("inbox", "sent", etc.)
        val uri = when (type.lowercase()) {
            "inbox" -> Telephony.Sms.Inbox.CONTENT_URI
            "sent" -> Telephony.Sms.Sent.CONTENT_URI
            "draft" -> Telephony.Sms.Draft.CONTENT_URI
            else -> Telephony.Sms.Inbox.CONTENT_URI
        }

        // Apply filter if query is provided (search in body or address)
        val selection = if (!query.isNullOrEmpty()) "body LIKE ? OR address LIKE ?" else null
        val selectionArgs = if (!query.isNullOrEmpty()) arrayOf("%$query%", "%$query%") else null

        // Sort messages by date with pagination
        val sortOrder = "date DESC LIMIT $count OFFSET $start"
        val list = mutableListOf<Map<String, Any>>()

        currentContext.contentResolver.query(
            uri,
            null,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val bodyIdx = cursor.getColumnIndex("body")
            val addressIdx = cursor.getColumnIndex("address")
            val dateIdx = cursor.getColumnIndex("date")
            val typeIdx = cursor.getColumnIndex("type")

            while (cursor.moveToNext()) {
                val body = if (bodyIdx >= 0) cursor.getString(bodyIdx) else ""
                val address = if (addressIdx >= 0) cursor.getString(addressIdx) else ""
                val date = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L
                val messageType = if (typeIdx >= 0) cursor.getString(typeIdx) else ""

                list.add(
                    mapOf(
                        "body" to body,
                        "address" to address,
                        "date" to date,
                        "type" to messageType
                    )
                )
            }
        }

        return list
    }

    private fun getMessageCount(type: String): Int {
        val currentContext = context ?: return 0

        val uri = when (type.lowercase()) {
            "inbox" -> Telephony.Sms.Inbox.CONTENT_URI
            "sent" -> Telephony.Sms.Sent.CONTENT_URI
            "draft" -> Telephony.Sms.Draft.CONTENT_URI
            else -> Telephony.Sms.Inbox.CONTENT_URI
        }

        return currentContext.contentResolver.query(uri, null, null, null, null)?.use {
            it.count
        } ?: 0
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        eventSink = events
        smsReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val pdus = intent?.extras?.get("pdus") as? Array<*> ?: return

                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent).map { sms ->
                    mapOf(
                        "address" to (sms.originatingAddress ?: ""),
                        "body" to sms.messageBody,
                        "date" to sms.timestampMillis,
                        "type" to "inbox"
                    )
                }

                messages.forEach { eventSink?.success(it) }
            }
        }

        // Register SMS broadcast receiver to listen for incoming messages
        try {
            context?.registerReceiver(
                smsReceiver,
                IntentFilter("android.provider.Telephony.SMS_RECEIVED")
            )
        } catch (e: Exception) {
            eventSink?.error("RECEIVER_ERROR", "Failed to register SMS receiver", e.message)
        }
    }

    override fun onCancel(arguments: Any?) {

        // Unregister receiver when event stream is cancelled
        // This avoids memory leaks and prevents duplicate events
        try {
            context?.unregisterReceiver(smsReceiver)
        } catch (_: Exception) {
            // Receiver might not be registered — safe to ignore
        }

        smsReceiver = null
        eventSink = null
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
    }

    override fun onDetachedFromActivityForConfigChanges() {}
    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {}
    override fun onDetachedFromActivity() {
        activity = null
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        eventChannel.setStreamHandler(null)
        context = null
    }
}