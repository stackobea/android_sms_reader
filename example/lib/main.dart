import 'package:android_sms_reader/android_sms_reader.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';

void main() {
  runApp(const MaterialApp(
    home: SMSReaderSampleApp(),
    debugShowCheckedModeBanner: false,
  ));
}

class SMSReaderSampleApp extends StatefulWidget {
  const SMSReaderSampleApp({super.key});

  @override
  createState() => _SMSReaderSampleAppState();
}

class _SMSReaderSampleAppState extends State<SMSReaderSampleApp> {
  List<AndroidSMSMessage> messages = [];
  Stream<AndroidSMSMessage>? smsStream;
  bool isListening = false;

  @override
  void initState() {
    super.initState();
    _checkAndRequestPermissions();
  }

  Future<void> _checkAndRequestPermissions() async {
    var status = await Permission.sms.status;

    if (!status.isGranted) {
      // Request permission
      status = await Permission.sms.request();
    }

    if (status.isGranted) {
      _initPlugin();
    } else {
      _showPermissionDialog();
    }
  }

  void _showPermissionDialog() {
    showDialog(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('Permission Required'),
        content: const Text('SMS permission is required to read messages.'),
        actions: [
          TextButton(
            child: const Text('Retry'),
            onPressed: () {
              Navigator.of(context).pop();
              _checkAndRequestPermissions();
            },
          ),
          TextButton(
            child: const Text('Cancel'),
            onPressed: () => Navigator.of(context).pop(),
          ),
        ],
      ),
    );
  }

  Future<void> _initPlugin() async {
    final granted = await AndroidSMSReader.requestPermissions();
    if (granted) {
      await _loadInitialMessages();
      _startSmsStream();
    } else {
      if (kDebugMode) {
        print("SMS permission denied");
      }
    }
  }

  Future<void> _loadInitialMessages() async {
    final fetched = await AndroidSMSReader.fetchMessages(
      type: AndroidSMSType.inbox,
      start: 0,
      count: 20,
    );
    setState(() {
      messages = fetched;
    });
  }

  void _startSmsStream() {
    if (isListening) return;
    smsStream = AndroidSMSReader.observeIncomingMessages();
    smsStream!.listen((AndroidSMSMessage message) {
      setState(() {
        messages.insert(0, message); // insert latest at top
      });
    });
    isListening = true;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('SMS Messages')),
      body: ListView.builder(
        itemCount: messages.length,
        itemBuilder: (context, index) {
          final msg = messages[index];
          return ListTile(
            title: Text(msg.address),
            subtitle: Text(msg.body),
            trailing:
                Text(DateTime.fromMillisecondsSinceEpoch(msg.date).toString()),
          );
        },
      ),
    );
  }
}
