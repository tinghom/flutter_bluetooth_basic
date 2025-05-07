package com.tablemi.flutter_bluetooth_basic;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.EventChannel.EventSink;
import io.flutter.plugin.common.EventChannel.StreamHandler;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.RequestPermissionsResultListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

/**
 * FlutterBluetoothBasicPlugin
 */
public class FlutterBluetoothBasicPlugin implements FlutterPlugin, ActivityAware, RequestPermissionsResultListener, MethodChannel.MethodCallHandler {
    private static final String TAG = "BluetoothBasicPlugin";
    private static final String NAMESPACE = "flutter_bluetooth_basic";
    private static final int REQUEST_PERMISSIONS = 1451;

    private Context applicationContext;
    private Activity activity;
    private MethodChannel methodChannel;
    private EventChannel stateChannel;

    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private ScanCallback scanCallback;
    private EventSink stateSink;

    private MethodCall pendingCall;
    private Result pendingResult;

    private ThreadPool threadPool;
    private int connectionId = 0;

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        applicationContext = binding.getApplicationContext();
        setupChannels(binding.getBinaryMessenger(), applicationContext);
    }

    private void setupChannels(BinaryMessenger messenger, Context context) {
        methodChannel = new MethodChannel(messenger, NAMESPACE + "/methods");
        stateChannel = new EventChannel(messenger, NAMESPACE + "/state");

        methodChannel.setMethodCallHandler(this);
        stateChannel.setStreamHandler(stateStreamHandler);

        bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
        }

        scanCallback = createScanCallback();
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        methodChannel.setMethodCallHandler(null);
        stateChannel.setStreamHandler(null);
        methodChannel = null;
        stateChannel = null;
        applicationContext = null;
    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        activity = binding.getActivity();
        binding.addRequestPermissionsResultListener(this);
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        activity = null;
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        activity = binding.getActivity();
        binding.addRequestPermissionsResultListener(this);
    }

    @Override
    public void onDetachedFromActivity() {
        activity = null;
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        if (bluetoothAdapter == null && !"isAvailable".equals(call.method)) {
            result.error("bluetooth_unavailable", "Bluetooth 無法使用", null);
            return;
        }

        Map<String, Object> args = call.arguments();
        switch (call.method) {
            case "state":
                getState(result);
                break;
            case "isAvailable":
                result.success(bluetoothAdapter != null);
                break;
            case "isOn":
                result.success(bluetoothAdapter.isEnabled());
                break;
            case "startScan":
                requestScanPermissions(call, result);
                break;
            case "stopScan":
                stopScan();
                result.success(null);
                break;
            case "connect":
                connectDevice(result, args);
                break;
            case "disconnect":
                result.success(disconnectDevice());
                break;
            case "destroy":
                result.success(destroyConnection());
                break;
            case "writeData":
                writeData(result, args);
                break;
            default:
                result.notImplemented();
                break;
        }
    }

    /**
     * 请求扫描所需权限（适配Android 12+蓝牙权限）
     */
    private void requestScanPermissions(MethodCall call, Result result) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            // Android 12+ 需要BLUETOOTH_SCAN权限
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.BLUETOOTH_SCAN},
                    REQUEST_PERMISSIONS);
                pendingCall = call;
                pendingResult = result;
                return;
            }
        } else {
            // 旧版本使用定位权限
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_PERMISSIONS);
                pendingCall = call;
                pendingResult = result;
                return;
            }
        }
        startScan(call, result);
    }

    /**
     * 创建扫描回调
     */
    private ScanCallback createScanCallback() {
        return new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                BluetoothDevice device = result.getDevice();
                if (device != null && device.getName() != null) {
                    invokeMethodOnUiThread("ScanResult", device);
                }
            }
        };
    }

    /**
     * 開始 BLE 掃描
     */
    private void startScan(MethodCall call, Result result) {
        try {
            BluetoothLeScanner scanner = bluetoothAdapter.getBluetoothLeScanner();
            if (scanner == null) throw new IllegalStateException("Adapter 未開啟，無法掃描");
            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();
            scanner.startScan(null, settings, scanCallback);
            result.success(null);
        } catch (Exception e) {
            result.error("startScan_error", e.getMessage(), null);
        }
    }

    /**
     * 停止 BLE 掃描
     */
    private void stopScan() {
        BluetoothLeScanner scanner = bluetoothAdapter.getBluetoothLeScanner();
        if (scanner != null) {
            scanner.stopScan(scanCallback);
        }
    }

    /**
     * 呼叫 UI 執行方法
     */
    private void invokeMethodOnUiThread(final String methodName, final BluetoothDevice device) {
        if (activity == null) return;
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("address", device.getAddress());
        resultMap.put("name", device.getName());
        resultMap.put("type", device.getType());
        activity.runOnUiThread(() -> methodChannel.invokeMethod(methodName, resultMap));
    }

    /**
     * 取得目前 Bluetooth 狀態
     */
    private void getState(Result result) {
        try {
            int state = bluetoothAdapter.getState();
            result.success(state);
        } catch (SecurityException e) {
            result.error("state_error", "取得狀態失敗", null);
        }
    }

    /**
     * 連線裝置
     */
    private void connectDevice(Result result, Map<String, Object> args) {
        if (args.containsKey("address")) {
            String macAddress = (String) args.get("address");
            disconnectDevice();
            new DeviceConnFactoryManager.Build()
                    .setId(connectionId)
                    .setConnMethod(DeviceConnFactoryManager.CONN_METHOD.BLUETOOTH)
                    .setMacAddress(macAddress)
                    .build();
            threadPool = ThreadPool.getInstantiation();
            threadPool.addSerialTask(() -> DeviceConnFactoryManager.getDeviceConnFactoryManagers()[connectionId].openPort());
            result.success(true);
        } else {
            result.error("invalid_arg", "缺少 address 參數", null);
        }
    }

    /**
     * 中斷連線
     */
    private boolean disconnectDevice() {
        if (DeviceConnFactoryManager.getDeviceConnFactoryManagers()[connectionId] != null
                && DeviceConnFactoryManager.getDeviceConnFactoryManagers()[connectionId].mPort != null) {
            DeviceConnFactoryManager.getDeviceConnFactoryManagers()[connectionId].reader.cancel();
            DeviceConnFactoryManager.getDeviceConnFactoryManagers()[connectionId].mPort.closePort();
            DeviceConnFactoryManager.getDeviceConnFactoryManagers()[connectionId].mPort = null;
        }
        return true;
    }

    /**
     * 終結所有資源
     */
    private boolean destroyConnection() {
        DeviceConnFactoryManager.closeAllPort();
        if (threadPool != null) {
            threadPool.stopThreadPool();
        }
        return true;
    }

    /**
     * 寫入資料
     */
    @SuppressWarnings("unchecked")
    private void writeData(Result result, Map<String, Object> args) {
        if (args.containsKey("bytes")) {
            ArrayList<Integer> byteList = (ArrayList<Integer>) args.get("bytes");
            threadPool = ThreadPool.getInstantiation();
            threadPool.addSerialTask(() -> {
                Vector<Byte> dataVector = new Vector<>();
                for (int val : byteList) {
                    dataVector.add((byte) (val > 127 ? val - 256 : val));
                }
                DeviceConnFactoryManager.getDeviceConnFactoryManagers()[connectionId].sendDataImmediately(dataVector);
            });
            result.success(null);
        } else {
            result.error("bytes_empty", "Bytes 參數為空", null);
        }
    }

    /**
     * 创建状态流处理器
     */
    private StreamHandler createStateStreamHandler() {
        return new StreamHandler() {
            private BroadcastReceiver stateReceiver;

            @Override
            public void onListen(Object arguments, EventSink events) {
                stateSink = events;
                stateReceiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        handleBluetoothStateChange(intent, events);
                    }
                };

                IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
                filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
                filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
                activity.registerReceiver(stateReceiver, filter);
            }

            @Override
            public void onCancel(Object arguments) {
                if (stateReceiver != null) {
                    activity.unregisterReceiver(stateReceiver);
                }
                stateSink = null;
            }
        };
    }

    /**
     * 处理蓝牙状态变化
     */
    private void handleBluetoothStateChange(Intent intent, EventSink sink) {
        String action = intent.getAction();
        if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
            sink.success(intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1));
        } else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
            sink.success(1);
        } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
            sink.success(0);
        }
    }

}
