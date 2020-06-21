package com.example.contacttracing;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import java.util.UUID;

/**Foreground Service for a Continuously running GATT Server
 * The GATT Server broadcasts the Unique ID of the User to all nearby BLE Enabled Devices
 */
public class GattServerService extends Service {

    private static final String TAG = GattServerService.class.getSimpleName();

    public static final String CHANNEL_ID = "GattServerServiceChannel";
    public static final UUID CONTACT_SERVICE_UUID = ContactProfile.CONTACT_SERVICE_UUID;
    public static final UUID CONTACT_CHARACTERISTIC_UUID = ContactProfile.CONTACT_CHARACTERISTIC_UUID;

    public BluetoothManager mBluetoothManager;
    private BluetoothGattServer mBluetoothGattServer;
    private BluetoothLeAdvertiser mBluetoothLeAdvertiser;


    MainActivity ma;

    @Override
    public void onCreate() {
        super.onCreate();
        mBluetoothManager = (BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();

        if(!checkBluetoothSupport(bluetoothAdapter)) {
            Toast.makeText(ma, "Bluetooth is Not Supported", Toast.LENGTH_SHORT).show();
        }


        if(!bluetoothAdapter.isEnabled()) {
            bluetoothAdapter.enable();
        } else {
            startAdvertising();
            startServer();
        }

    }

    private boolean checkBluetoothSupport(BluetoothAdapter bluetoothAdapter) {
        if(bluetoothAdapter==null||!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)){
            return false;
        }

        return true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent,flags,startId);

        createNotificationChannel();
        Intent notificationIntent = new Intent(this,MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,0,notificationIntent,0);

        Notification notification = new
                NotificationCompat.Builder(this,CHANNEL_ID)
                    .setContentTitle("Gatt Server Service")
                    .setContentText("Gatt Server Running")
                    .setContentIntent(pendingIntent)
                    .build();

        startForeground(1,notification);

        return START_NOT_STICKY;
    }


    @Override
    public void onDestroy() {
        super.onDestroy();

        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();

        if(bluetoothAdapter.isEnabled()) {
            stopServer();
            stopAdvertising();
        }
    }

    private void createNotificationChannel() {
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new
                    NotificationChannel(
                            CHANNEL_ID,
                    "Gatt Server Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(serviceChannel);
        }
    }

    /**
     * Set the GATT Server Settings, Start Advertising*
    * */
    private void startAdvertising() {
        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
        mBluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();

        if(mBluetoothLeAdvertiser==null) {
            Log.w(this.getClass().getSimpleName(), "Failed to Create Advertiser");
            return;
        }

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(new ParcelUuid(CONTACT_SERVICE_UUID))
                .build();

        mBluetoothLeAdvertiser
                .startAdvertising(settings,data,mAdvertiseCallback);
    }

    private void stopAdvertising() {
        if(mBluetoothLeAdvertiser==null) {
            return;
        }

        mBluetoothLeAdvertiser.stopAdvertising(mAdvertiseCallback);
    }

    /**
     * Add the ContactID Service to the GATT Server
     */
    private void startServer() {
        mBluetoothGattServer = mBluetoothManager.openGattServer(this,mGattServerCallBack);

        mBluetoothGattServer.addService(ContactProfile.createContactService());
    }

    private void stopServer() {
        if(mBluetoothGattServer==null) {
            return;
        }

        mBluetoothGattServer.close();
    }

    private AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            Log.i(TAG, "onStartSuccess: LE Advertise Started");
        }

        @Override
        public void onStartFailure(int errorCode) {
            Log.w(TAG, "onStartFailure: LE Advertise Failed"+errorCode);
        }
    };

    private BluetoothGattServerCallback mGattServerCallBack = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);
            if(newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG,"BluetoothDevice CONNECTED:"+device);
            } else if(newState==BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "BluetoothDevice DISCONNECTED:"+device);
            }
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);

            //If the Requested Characteristic is a valid one, send the characteristic data over BLE
            if(ContactProfile.CONTACT_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                Log.i(TAG,"Read Contact Characteristic");
                //Set the Characteristic Data as the User's UUID
                String data = JWT_Token_Handler.getUUIDFromToken(PreferenceData.getUserJwt(getApplicationContext()));
                Log.e(TAG, "onCharacteristicReadRequest: " + data);
                mBluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        data.getBytes());
            } else {
                //Invalid Characteristic
                Log.i(TAG, "Invalid Characteristic Read: "+characteristic.getUuid());
                mBluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_FAILURE,
                        0,
                        null);
            }
        }
    };



    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
