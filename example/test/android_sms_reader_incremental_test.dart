import 'package:android_sms_reader/android_sms_reader.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const channel = MethodChannel('android_sms_reader');
  final calls = <MethodCall>[];
  Object? Function(MethodCall)? responder;

  setUp(() {
    calls.clear();
    responder = null;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
      calls.add(call);
      final r = responder;
      return r == null ? null : r(call);
    });
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });

  test('fetchInboxAfter sends checkpoint args and maps id', () async {
    responder = (_) => [
          {
            'id': 7,
            'address': 'BANK',
            'body': 'debited',
            'date': 1700000000000,
            'type': 'inbox'
          }
        ];
    final r = await AndroidSMSReader.fetchInboxAfter(
        afterDate: 123, afterId: 4, count: 10);

    expect(calls.single.method, 'fetchInboxAfter');
    expect(
        calls.single.arguments, {'afterDate': 123, 'afterId': 4, 'count': 10});
    expect(r.single.id, 7);
    expect(r.single.date, 1700000000000);
  });

  test('fetchInboxAfter: null/empty native result -> empty list', () async {
    expect(await AndroidSMSReader.fetchInboxAfter(afterDate: 0, afterId: 0),
        isEmpty);
  });

  test('fetchInboxAfter rejects invalid count', () async {
    expect(
        () => AndroidSMSReader.fetchInboxAfter(
            afterDate: 0, afterId: 0, count: 0),
        throwsArgumentError);
    expect(
        () => AndroidSMSReader.fetchInboxAfter(
            afterDate: 0,
            afterId: 0,
            count: AndroidSMSReader.maxInboxBatch + 1),
        throwsArgumentError);
  });

  test('PlatformException from native propagates', () async {
    responder = (_) => throw PlatformException(code: 'PERMISSION_DENIED');
    await expectLater(
      AndroidSMSReader.fetchInboxAfter(afterDate: 0, afterId: 0),
      throwsA(isA<PlatformException>()
          .having((e) => e.code, 'code', 'PERMISSION_DENIED')),
    );
  });

  test('countInboxAfter returns native count, null -> 0', () async {
    responder = (_) => 3;
    expect(await AndroidSMSReader.countInboxAfter(afterDate: 1, afterId: 2), 3);
    responder = (_) => null;
    expect(await AndroidSMSReader.countInboxAfter(afterDate: 1, afterId: 2), 0);
  });

  test('AndroidSMSMessage.fromJson: id populated, missing id -> 0', () {
    final m = AndroidSMSMessage.fromJson(
        {'id': 9, 'address': 'a', 'body': 'b', 'date': 1, 'type': 'inbox'});
    expect(m.id, 9);
    final old = AndroidSMSMessage.fromJson(
        {'address': 'a', 'body': 'b', 'date': 1, 'type': 'inbox'});
    expect(old.id, 0);
  });
}
