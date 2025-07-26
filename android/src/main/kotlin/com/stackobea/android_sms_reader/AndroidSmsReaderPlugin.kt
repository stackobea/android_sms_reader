package com.stackobea.android_sms_reader

import android.Manifest
import android.app.Activity
import android.content.*
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.*
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import org.json.JSONObject

class AndroidSmsReaderPlugin : FlutterPlugin, MethodChannel.MethodCallHandler,
    EventChannel.StreamHandler, ActivityAware {
    private lateinit var channel: MethodChannel
    private lateinit var eventChannel: EventChannel
    private var context: Context? = null
    private var activity: Activity? = null
    private var smsReceiver: BroadcastReceiver? = null
    private var eventSink: EventChannel.EventSink? = null

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(binding.binaryMessenger, "android_sms_reader")
        eventChannel = EventChannel(binding.binaryMessenger, "sms_observer")
        channel.setMethodCallHandler(this)
        eventChannel.setStreamHandler(this)
        context = binding.applicationContext
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "requestPermissions" -> {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context!!,
                    Manifest.permission.READ_SMS
                ) == PackageManager.PERMISSION_GRANTED
                result.success(hasPermission)
            }

            "fetchMessages" -> {
                val type = call.argument<String>("type")
                val start = call.argument<Int>("start") ?: 0
                val count = call.argument<Int>("count") ?: 50
                val query = call.argument<String>("query")
                val messages = fetchMessages(type!!, start, count, query)
                result.success(messages)
            }

            "getMessageCount" -> {
                val type = call.argument<String>("type")
                result.success(getMessageCount(type!!))
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
        val uri = when (type) {
            "inbox" -> Telephony.Sms.Inbox.CONTENT_URI
            "sent" -> Telephony.Sms.Sent.CONTENT_URI
            "draft" -> Telephony.Sms.Draft.CONTENT_URI
            else -> Telephony.Sms.Inbox.CONTENT_URI
        }

        val selection = if (query != null) "body LIKE ? OR address LIKE ?" else null
        val selectionArgs = if (query != null) arrayOf("%$query%", "%$query%") else null
        val cursor = context!!.contentResolver.query(
            uri,
            null,
            selection,
            selectionArgs,
            "date DESC LIMIT $count OFFSET $start"
        )

        val list = mutableListOf<Map<String, Any>>()
        cursor?.use {
            while (it.moveToNext()) {
                val body = it.getString(it.getColumnIndexOrThrow("body"))
                val address = it.getString(it.getColumnIndexOrThrow("address"))
                val date = it.getLong(it.getColumnIndexOrThrow("date"))
                val messageType = it.getString(it.getColumnIndexOrThrow("type"))
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
        val uri = when (type) {
            "inbox" -> Telephony.Sms.Inbox.CONTENT_URI
            "sent" -> Telephony.Sms.Sent.CONTENT_URI
            "draft" -> Telephony.Sms.Draft.CONTENT_URI
            else -> Telephony.Sms.Inbox.CONTENT_URI
        }
        val cursor = context!!.contentResolver.query(uri, null, null, null, null)
        return cursor?.count ?: 0
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        eventSink = events
        smsReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val extras = intent?.extras ?: return
                val pdus = extras.get("pdus") as? Array<*>
                val messages = pdus?.map {
                    val sms = Telephony.Sms.Intents.getMessagesFromIntent(intent)[0]
                    mapOf(
                        "address" to sms.originatingAddress,
                        "body" to sms.messageBody,
                        "date" to sms.timestampMillis,
                        "type" to "inbox"
                    )
                }
                messages?.forEach { eventSink?.success(it) }
            }
        }
        context?.registerReceiver(
            smsReceiver,
            IntentFilter("android.provider.Telephony.SMS_RECEIVED")
        )
    }

    override fun onCancel(arguments: Any?) {
        context?.unregisterReceiver(smsReceiver)
        smsReceiver = null
        eventSink = null
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
    }

    override fun onDetachedFromActivityForConfigChanges() {}
    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {}
    override fun onDetachedFromActivity() {}

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        eventChannel.setStreamHandler(null)
    }
}