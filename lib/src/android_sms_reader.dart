import 'dart:async';

import 'package:flutter/services.dart';

import 'models/android_sms_message.dart';
import 'models/android_sms_type.dart';

class AndroidSMSReader {
  static const MethodChannel _channel = MethodChannel('android_sms_reader');
  static const EventChannel _eventChannel = EventChannel('sms_observer');

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

  static Future<int> getMessageCount(AndroidSMSType type) async {
    return await _channel.invokeMethod('getMessageCount', {'type': type.name});
  }

  static Future<List<AndroidSMSMessage>> exportToJson(
      AndroidSMSType type) async {
    return fetchMessages(type: type);
  }

  static Stream<AndroidSMSMessage> observeIncomingMessages() {
    return _eventChannel.receiveBroadcastStream().map((event) {
      return AndroidSMSMessage.fromJson(Map<String, dynamic>.from(event));
    });
  }

  static Future<bool> requestPermissions() async {
    return await _channel.invokeMethod('requestPermissions');
  }
}
