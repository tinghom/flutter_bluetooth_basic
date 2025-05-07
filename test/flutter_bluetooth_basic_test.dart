import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_bluetooth_basic/flutter_bluetooth_basic.dart';

void main() {
  final binding = TestWidgetsFlutterBinding.ensureInitialized();
  const MethodChannel _channel =
      MethodChannel('flutter_bluetooth_basic/methods');
  final List<MethodCall> log = <MethodCall>[];

  group('FlutterBluetoothBasicPlugin', () {
    setUp(() {
      log.clear();
      // 攔截所有 platform calls
      binding.defaultBinaryMessenger.setMockMethodCallHandler(_channel,
          (MethodCall methodCall) async {
        log.add(methodCall);
        switch (methodCall.method) {
          case 'state':
            return 1; // 模擬 STATE_ON
          case 'startScan':
            return null;
          case 'stopScan':
            return null;
          case 'connect':
            return true;
          case 'disconnect':
            return true;
          case 'writeData':
            return null;
          default:
            throw PlatformException(code: 'not_implemented');
        }
      });
    });

    tearDown(() {
      // 解除攔截
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(_channel, null);
      log.clear();
    });

    test('state returns state', () async {
      final manager = BluetoothManager.instance;
      final state = await manager.state.first;
      expect(state, BluetoothManager.CONNECTED);
      expect(log.first.method, 'state');
    });

    test('startScan invokes startScan', () async {
      final manager = BluetoothManager.instance;
      await manager.startScan(timeout: const Duration(milliseconds: 10));
      // 确认至少有两个调用：startScan 与 stopScan
      expect(log.map((c) => c.method).toList(),
          containsAll(['startScan', 'stopScan']));
    });

    test('connect invokes connect with address', () async {
      final manager = BluetoothManager.instance;
      final ok =
          await manager.connect(BluetoothDevice(address: '00:11:22:33:44:55'));
      expect(ok, isTrue);

      final connectCall = log.firstWhere(
        (c) => c.method == 'connect',
        orElse: () => throw Exception('未發現 connect 調用'),
      );
      final args = connectCall.arguments;

      expect(args, containsPair('address', '00:11:22:33:44:55'));
    });

    test('disconnect invokes disconnect', () async {
      final manager = BluetoothManager.instance;
      final ok = await manager.disconnect();
      expect(ok, isTrue);
      expect(log.any((c) => c.method == 'disconnect'), isTrue);
    });

    test('writeData invokes writeData with bytes', () async {
      final manager = BluetoothManager.instance;
      final data = <int>[1, 2, 3];
      await manager.writeData(data);
      final writeCall = log.firstWhere((c) => c.method == 'writeData',
          orElse: () => throw '');
      final args = writeCall.arguments as Map;
      expect(args['bytes'], data);
    });
  });
}
