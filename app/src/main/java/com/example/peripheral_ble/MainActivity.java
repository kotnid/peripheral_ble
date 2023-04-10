package com.example.peripheral_ble;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelUuid;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "BlePeripheralActivity";

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGattServer mGattServer;
    private BluetoothGattService mGattService;
    private BluetoothGattCharacteristic mGattCharacteristic;
    int REQUEST_BLUETOOTH_PERMISSIONS = 127;
    int REQUEST_CONNECTION_PERMISSIONS = 128;
    int REQUEST_COARSE_LOCATION = 129;

    private Handler mHandler = new Handler() {
        @SuppressLint({"MissingPermission", "HandlerLeak"})
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    Log.d(TAG, "Characteristic read: " + mGattCharacteristic.getValue().toString());
                    mGattServer.sendResponse(
                            (BluetoothDevice) msg.obj,
                            msg.arg1,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            mGattCharacteristic.getValue()
                    );
                    break;
                case 2:
                    byte[] byteValue = mGattCharacteristic.getValue();
                    if (byteValue != null) {
                        String stringValue = new String(byteValue, StandardCharsets.UTF_8);
                        Log.d(TAG, "Value: " + stringValue);
                    }
                    mGattServer.sendResponse(
                            (BluetoothDevice) msg.obj,
                            msg.arg1,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            mGattCharacteristic.getValue()
                    );
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    };

    private BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            Log.d(TAG, "onConnectionStateChange: " + newState);
        }

        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
            Log.d(TAG, "onServiceAdded");
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            Log.d(TAG, "onCharacteristicReadRequest");
            Message msg = mHandler.obtainMessage(1, requestId, offset, device);
            mHandler.sendMessage(msg);
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            Log.d(TAG, "onCharacteristicWriteRequest");
            characteristic.setValue(value);
            Message msg = mHandler.obtainMessage(2, requestId, offset, device);
            mHandler.sendMessage(msg);
        }
    };
    public void checkPermission(){
        ActivityResultLauncher<Intent> enableBtLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != Activity.RESULT_OK) {
                        toastmsg("Error : permission denied");
                    }
                });
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            enableBtLauncher.launch(enableBtIntent);
        }
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            enableBtLauncher.launch(intent);
        }

        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_COARSE_LOCATION);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= 31) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BLUETOOTH_SCAN},
                    REQUEST_BLUETOOTH_PERMISSIONS);
            toastmsg("Request permission");
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Init
        mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        checkPermission();

        Button startbtn = findViewById(R.id.startBtn);

        startbtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                // Create a GATT server and service
                mGattServer = mBluetoothManager.openGattServer(getApplicationContext(), mGattServerCallback);
                mGattService = new BluetoothGattService(UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E"), BluetoothGattService.SERVICE_TYPE_PRIMARY);
                mGattCharacteristic = new BluetoothGattCharacteristic(UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E"), BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE);
                mGattService.addCharacteristic(mGattCharacteristic);
                mGattServer.addService(mGattService);

                // Advertise the service
                mBluetoothAdapter.setName("tkt BLE");
                mBluetoothAdapter.getBluetoothLeAdvertiser().startAdvertising(
                        new AdvertiseSettings.Builder()
                                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                                .setConnectable(true)
                                .setTimeout(0)
                                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                                .build(),
                        new AdvertiseData.Builder()
                                .addServiceUuid(ParcelUuid.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E"))
                                .setIncludeDeviceName(true)
                                .build(),
                        advertiseCallback);
            }
        });
    }

    AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            Log.d(TAG , "Success");
        }
    };

    @SuppressLint("MissingPermission")
    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Stop advertising and disconnect the GATT server
        mBluetoothAdapter.getBluetoothLeAdvertiser().stopAdvertising(advertiseCallback);
        mGattServer.close();
    }

    public void toastmsg(String msg){
        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
    }


}
