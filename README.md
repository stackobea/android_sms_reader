# android_sms_reader

A Flutter plugin to **read SMS messages on Android**. Supports inbox, sent, and draft messages with
pagination, search, streaming of incoming messages, and permission handling.

> ⚠️ This plugin is Android-only. iOS does not allow SMS access due to platform restrictions.

---

## 🚀 Features

| Feature                  | Description                                 |
|--------------------------|---------------------------------------------|
| ✅ Fetch All Messages     | Access all SMS on the device                |
| ✅ Filter by Type         | Fetch only inbox, sent, or draft messages   |
| ✅ Pagination Support     | Lazy loading of messages                    |
| ✅ Search SMS             | Filter by keyword or phone number           |
| ✅ Message Count          | Total number of messages per type           |
| ✅ Observe New SMS        | Stream new incoming messages in real-time   |
| ✅ Read Permissions       | Auto-handle permission requests             |
| ✅ Dual SIM Support (WIP) | Read SIM info (planned)                     |
| ✅ Export to JSON         | Easily convert messages for sharing/storage |

---

## 🛠 Installation

Add this to your `pubspec.yaml`:

```yaml
dependencies:
  android_sms_reader: ^0.0.1

```

Then run:
```bash
flutter pub get
```

## Usage

```dart
import 'package:android_sms_reader/android_sms_reader.dart';

void init() async {
  bool granted = await SmsReader.requestPermissions();
  if (!granted) {
    // handle permission
    return;
  }

  final messages = await SmsReader.fetchMessages(
    type: SmsType.inbox,
    start: 0,
    count: 50,
    query: 'OTP',
  );

  final count = await SmsReader.getMessageCount(SmsType.sent);

  SmsReader.observeIncomingMessages().listen((sms) {
    print("Received: ${sms.body}");
  });
}
```

## ❗ Android Permissions

Ensure you add these to your AndroidManifest.xml:

```xml

<uses-permission android:name="android.permission.READ_SMS" />
<uses-permission android:name="android.permission.RECEIVE_SMS" />
```

## ❌ iOS Support

iOS does not support reading SMS messages. This plugin returns UnsupportedError on iOS platforms.