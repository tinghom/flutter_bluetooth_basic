// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'bluetooth_device.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

BluetoothDevice _$BluetoothDeviceFromJson(Map<String, dynamic> json) =>
    BluetoothDevice(
      name: json['name'] as String?,
      address: json['address'] as String?,
      type: (json['type'] as num?)?.toInt() ?? 0,
      connected: json['connected'] as bool? ?? false,
    );

Map<String, dynamic> _$BluetoothDeviceToJson(BluetoothDevice instance) =>
    <String, dynamic>{
      if (instance.name case final value?) 'name': value,
      if (instance.address case final value?) 'address': value,
      if (instance.type case final value?) 'type': value,
      if (instance.connected case final value?) 'connected': value,
    };
