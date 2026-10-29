# Flutter Android SMS Reader

[![Pub Version](https://img.shields.io/pub/v/android_sms_reader)](https://pub.dev/packages/android_sms_reader)
[![Pub Likes](https://img.shields.io/pub/likes/android_sms_reader)](https://pub.dev/packages/android_sms_reader)
[![Pub Points](https://img.shields.io/pub/points/android_sms_reader)](https://pub.dev/packages/android_sms_reader)
[![Popularity](https://img.shields.io/pub/popularity/android_sms_reader)](https://pub.dev/packages/android_sms_reader)

A Flutter plugin to **read SMS messages on Android**. Supports inbox, sent, and draft messages with
pagination, search, **incremental (checkpoint-based) reads**, streaming of incoming messages, and
permission checks.

> ⚠️ Android only. iOS does not allow apps to read SMS. Guard calls with `Platform.isAndroid`;
> on other platforms the calls throw `MissingPluginException`.

---

## 🚀 Features

| Feature                    | Description                                                        |
|----------------------------|--------------------------------------------------------------------|
| ✅ Fetch Messages           | Read SMS from the device                                           |
| ✅ Filter by Type           | Inbox, sent, or draft                                              |
| ✅ Pagination               | Offset-based paging with `start` / `count`                         |
| ✅ Search SMS               | Filter by keyword or phone number                                  |
| ✅ **Incremental Fetch**    | Read only messages **newer than a checkpoint** (native-filtered)   |
| ✅ Message Count            | Total messages per type, or count of new messages after checkpoint |
| ✅ Observe New SMS          | Stream incoming messages in real time                              |
| ✅ Permission Check         | Check whether `READ_SMS` is granted                                |
| ✅ Export to JSON           | `AndroidSMSMessage.toJson()` for sharing/storage                   |
| 🚧 Dual SIM Support         | Planned                                                            |

---

## 🛠 Installation

```yaml
dependencies:
  android_sms_reader: ^<Latest-Version>
  permission_handler: ^<Latest-Version>   # recommended, to show the runtime permission dialog
```

```bash
flutter pub get
```

## ❗ Android Permissions

Add to `android/app/src/main/AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.READ_SMS" />
<!-- Only needed for observeIncomingMessages() -->
<uses-permission android:name="android.permission.RECEIVE_SMS" />
```

> **Google Play:** `READ_SMS` and `RECEIVE_SMS` are *restricted permissions*. Apps that request
> them must complete Google Play's SMS/Call Log permissions declaration and qualify for an allowed
> use case. Plan for this before you publish.

`AndroidSMSReader.requestPermissions()` reports whether `READ_SMS` is currently granted; it does
not show a dialog. Use `permission_handler` (or similar) to ask the user:

```dart
import 'package:permission_handler/permission_handler.dart';

Future<bool> ensureSmsPermission() async {
  if (await Permission.sms.isGranted) return true;
  return (await Permission.sms.request()).isGranted;
}
```

---

## 📲 Basic Usage

```dart
import 'package:android_sms_reader/android_sms_reader.dart';

Future<void> demo() async {
  if (!await ensureSmsPermission()) return;

  // Paged read (newest first)
  final messages = await AndroidSMSReader.fetchMessages(
    type: AndroidSMSType.inbox,
    start: 0,
    count: 50,
    query: 'OTP', // optional: matches body or address
  );

  final sentCount = await AndroidSMSReader.getMessageCount(AndroidSMSType.sent);

  // Live stream of incoming SMS (needs RECEIVE_SMS)
  AndroidSMSReader.observeIncomingMessages().listen((sms) {
    print('Received: ${sms.body}');
  });
}
```

### The `AndroidSMSMessage` model

| Field     | Meaning                                                                          |
|-----------|----------------------------------------------------------------------------------|
| `id`      | Android SMS provider `_id`. `0` means unknown (see notes below)                  |
| `address` | Sender / receiver address                                                        |
| `body`    | Message text                                                                     |
| `date`    | Timestamp in **milliseconds since epoch**                                        |
| `type`    | `inbox`, `sent`, ...                                                             |

---

## 🔄 Incremental fetch (only new messages)

Offset paging (`fetchMessages(start: ...)`) can't reliably find new messages, because every new SMS
shifts all offsets. Use `fetchInboxAfter` instead: it asks Android for inbox messages **strictly
after** a checkpoint and returns them oldest-first, so you can process them in order and save your
progress.

A checkpoint is the pair `(date, id)` of the last message you processed. Start with `(0, 0)` to read
from the beginning.

```dart
import 'package:android_sms_reader/android_sms_reader.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Processes every inbox SMS received since the last successful run.
Future<void> syncNewMessages() async {
  final prefs = await SharedPreferences.getInstance();
  var afterDate = prefs.getInt('sms_cp_date') ?? 0; // 0 = from the beginning
  var afterId = prefs.getInt('sms_cp_id') ?? 0;

  try {
    // Optional: how many are waiting (handy for a progress bar)
    final pending = await AndroidSMSReader.countInboxAfter(
      afterDate: afterDate,
      afterId: afterId,
    );
    if (pending == 0) return; // nothing new

    while (true) {
      final batch = await AndroidSMSReader.fetchInboxAfter(
        afterDate: afterDate,
        afterId: afterId,
        count: 100, // 1..500
      );
      if (batch.isEmpty) break; // done (also catches SMS that arrived meanwhile)

      for (final sms in batch) {
        await handleSms(sms); // your logic
      }

      // Advance the checkpoint ONLY after the batch was handled successfully.
      afterDate = batch.last.date;
      afterId = batch.last.id;
      await prefs.setInt('sms_cp_date', afterDate);
      await prefs.setInt('sms_cp_id', afterId);
    }
  } on PlatformException catch (e) {
    if (e.code == 'PERMISSION_DENIED') {
      // ask for READ_SMS and retry later
    } else {
      // QUERY_FAILED: the checkpoint was not advanced, so retrying is safe
    }
  }
}
```

**Why `(date, id)`?** Two messages can share the same timestamp. The `id` breaks the tie, so none
are skipped at a batch boundary.

**Ordering and paging:** results are sorted by `date ASC, id ASC`. To get the next page, pass the
`date` and `id` of the **last** message of the previous batch.

**Errors:**

| Code                | Meaning                                  |
|---------------------|------------------------------------------|
| `PERMISSION_DENIED` | `READ_SMS` is not granted                |
| `QUERY_FAILED`      | The SMS content provider query failed    |

An invalid `count` (outside 1..500) throws `ArgumentError`.

### Notes and limitations

- Only the **inbox** is supported by the incremental methods.
- Messages inserted with a `date` **older than your checkpoint** (restored from a backup, imported
  by another app, large device-clock change) won't be returned. To include them, rescan from
  `afterDate: 0, afterId: 0`.
- Deleted messages simply stop appearing; nothing is returned for them.
- Messages from `observeIncomingMessages()` have `id == 0`, because Android announces an SMS before
  it is stored. Use them for live notifications only; use `fetchInboxAfter` to build a checkpoint.

---

## ❌ iOS

iOS does not allow reading SMS. This plugin has no iOS implementation; wrap calls in
`if (Platform.isAndroid)`.

## 📝 Changelog

See [CHANGELOG.md](CHANGELOG.md).