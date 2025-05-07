import 'package:json_annotation/json_annotation.dart';

part 'bluetooth_device.g.dart';

@JsonSerializable(includeIfNull: false)
class BluetoothDevice {
  BluetoothDevice({
    this.name,
    this.address,
    this.type = 0,
    this.connected = false,
  });

  String? name;
  String? address;
  int? type = 0;
  bool? connected = false;

  factory BluetoothDevice.fromJson(Map<String, dynamic> json) =>
      _$BluetoothDeviceFromJson(json);
  Map<String, dynamic> toJson() => _$BluetoothDeviceToJson(this);
}
