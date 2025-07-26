import 'dart:async';

import 'package:flutter/services.dart';

import 'models/android_sms_message.dart';
import 'models/android_sms_type.dart';

/// The main entry point for interacting with the Android SMS Reader plugin.
///
/// Provides methods to fetch, filter, search, and observe SMS messages from
/// an Android device. Also handles permission requests and supports exporting data.
class AndroidSMSReader {
  /// The method channel used to communicate with the Android platform code.
  static const MethodChannel _channel = MethodChannel('android_sms_reader');

  /// The event channel used to receive incoming SMS messages in real-time.
  static const EventChannel _eventChannel = EventChannel('sms_observer');

  /// Fetches a list of SMS messages from the device.
  ///
  /// [type]: Type of messages to fetch (e.g., inbox, sent, draft).
  /// [start]: Index to start pagination from.
  /// [count]: Number of messages to fetch.
  /// [query]: Optional search keyword (e.g., phone number or message text).
  ///
  /// Returns a list of [AndroidSMSMessage] objects.
  static Future<List<AndroidSMSMessage>> fetchMessages({
    required AndroidSMSType type,
    int start = 0,
    int count = 50,
    String? query,
  }) async {
    final result = await _channel.invokeMethod('fetchMessages', {
      'type': type.name,
      'start': start,
      'count': count,
      'query': query,
    });

    return (result as List)
        .map((e) => AndroidSMSMessage.fromJson(Map<String, dynamic>.from(e)))
        .toList();
  }

  /// Returns the total number of messages for a given [type].
  ///
  /// Example: Get the count of inbox messages.
  static Future<int> getMessageCount(AndroidSMSType type) async {
    return await _channel.invokeMethod('getMessageCount', {'type': type.name});
  }

  /// Exports messages to JSON format by fetching them using the given [type].
  ///
  /// Useful for sharing or storing SMS data.
  static Future<List<AndroidSMSMessage>> exportToJson(
      AndroidSMSType type) async {
    return fetchMessages(type: type);
  }

  /// Observes and listens to new incoming SMS messages in real-time.
  ///
  /// Returns a [Stream] of [AndroidSMSMessage] when a new SMS is received.
  static Stream<AndroidSMSMessage> observeIncomingMessages() {
    return _eventChannel.receiveBroadcastStream().map((event) {
      return AndroidSMSMessage.fromJson(Map<String, dynamic>.from(event));
    });
  }

  /// Requests runtime permissions to read SMS messages from the device.
  ///
  /// Returns `true` if permissions are granted, otherwise `false`.
  static Future<bool> requestPermissions() async {
    return await _channel.invokeMethod('requestPermissions');
  }
}
