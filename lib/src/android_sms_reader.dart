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

  /// Largest batch the native side will return from [fetchInboxAfter].
  /// Larger requests are rejected with [ArgumentError] rather than silently
  /// truncated.
  static const int maxInboxBatch = 500;

  /// Fetches a list of SMS messages from the device.
  ///
  /// [type]: Type of messages to fetch (e.g., inbox, sent, draft).
  /// [start]: Index to start pagination from.
  /// [count]: Number of messages to fetch.
  /// [query]: Optional search keyword (e.g., phone number or message text).
  ///
  /// Returns a list of [AndroidSMSMessage] objects.
  ///
  /// Offset pagination is unstable while the inbox changes (a new SMS shifts
  /// every offset). To read only new messages use [fetchInboxAfter].
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

  /// Fetches inbox messages strictly **after** a checkpoint.
  ///
  /// A message is "after" the checkpoint when its [AndroidSMSMessage.date] is
  /// greater than [afterDate], or equal to it with an [AndroidSMSMessage.id]
  /// greater than [afterId]. Results are ordered by `date ASC, id ASC` and
  /// limited to [count] (1..[maxInboxBatch]).
  ///
  /// To page forward, pass the `date` and `id` of the **last** message of the
  /// previous batch. Start from the beginning with `afterDate: 0, afterId: 0`.
  ///
  /// Returns an empty list when nothing is newer.
  ///
  /// Throws [PlatformException] with code `PERMISSION_DENIED` (READ_SMS not
  /// granted) or `QUERY_FAILED`. Throws [ArgumentError] for an invalid [count].
  ///
  /// Limitation: messages inserted with a `date` older than the checkpoint
  /// (backup restore, import, large clock change) are not returned; use a full
  /// scan via `afterDate: 0, afterId: 0` to pick those up.
  static Future<List<AndroidSMSMessage>> fetchInboxAfter({
    required int afterDate,
    required int afterId,
    int count = 50,
  }) async {
    if (count < 1 || count > maxInboxBatch) {
      throw ArgumentError.value(
          count, 'count', 'must be between 1 and $maxInboxBatch');
    }
    final raw = await _channel.invokeMethod<List<dynamic>>('fetchInboxAfter', {
      'afterDate': afterDate,
      'afterId': afterId,
      'count': count,
    });
    return (raw ?? const [])
        .map((e) =>
            AndroidSMSMessage.fromJson(Map<String, dynamic>.from(e as Map)))
        .toList();
  }

  /// Number of inbox messages strictly after the checkpoint (same semantics
  /// as [fetchInboxAfter]). Useful for progress totals.
  ///
  /// Throws [PlatformException] with code `PERMISSION_DENIED` or
  /// `QUERY_FAILED`.
  static Future<int> countInboxAfter({
    required int afterDate,
    required int afterId,
  }) async {
    final n = await _channel.invokeMethod<int>('countInboxAfter', {
      'afterDate': afterDate,
      'afterId': afterId,
    });
    return n ?? 0;
  }
}
