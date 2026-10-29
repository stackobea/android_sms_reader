/// A model representing a single SMS message on Android.
class AndroidSMSMessage {
  /// The sender ID
  /// Android SMS provider's stable `_id` (Telephony.Sms._ID).
  final int id;

  /// The sender or receiver address (phone number) of the SMS message.
  final String address;

  /// The body/content of the SMS message.
  final String body;

  /// The timestamp of the message in milliseconds since epoch.
  final int date;

  /// The type of SMS (e.g., inbox, sent).
  final String type;

  /// Constructs an [AndroidSMSMessage] with the required fields.
  AndroidSMSMessage({
    required this.id,
    required this.address,
    required this.body,
    required this.date,
    required this.type,
  });

  /// Creates an [AndroidSMSMessage] from a JSON [Map].
  factory AndroidSMSMessage.fromJson(Map<String, dynamic> json) =>
      AndroidSMSMessage(
        id: (json['id'] as num?)?.toInt() ?? 0,
        address: (json['address'] as String?) ?? '',
        body: (json['body'] as String?) ?? '',
        date: (json['date'] as num).toInt(),
        type: (json['type'] as String?) ?? '',
      );

  /// Converts this [AndroidSMSMessage] instance to a JSON [Map].
  Map<String, dynamic> toJson() => {
        'id': id,
        'address': address,
        'body': body,
        'date': date,
        'type': type,
      };
}
