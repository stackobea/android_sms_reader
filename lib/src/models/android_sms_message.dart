/// A model representing a single SMS message on Android.
class AndroidSMSMessage {
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
    required this.address,
    required this.body,
    required this.date,
    required this.type,
  });

  /// Creates an [AndroidSMSMessage] from a JSON [Map].
  factory AndroidSMSMessage.fromJson(Map<String, dynamic> json) =>
      AndroidSMSMessage(
        address: json['address'],
        body: json['body'],
        date: json['date'],
        type: json['type'],
      );

  /// Converts this [AndroidSMSMessage] instance to a JSON [Map].
  Map<String, dynamic> toJson() => {
        'address': address,
        'body': body,
        'date': date,
        'type': type,
      };
}
