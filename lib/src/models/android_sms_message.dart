class AndroidSMSMessage {
  final String address;
  final String body;
  final int date;
  final String type;

  AndroidSMSMessage({
    required this.address,
    required this.body,
    required this.date,
    required this.type,
  });

  factory AndroidSMSMessage.fromJson(Map<String, dynamic> json) =>
      AndroidSMSMessage(
        address: json['address'],
        body: json['body'],
        date: json['date'],
        type: json['type'],
      );

  Map<String, dynamic> toJson() => {
        'address': address,
        'body': body,
        'date': date,
        'type': type,
      };
}
