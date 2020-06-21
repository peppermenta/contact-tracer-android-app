package com.example.contacttracing;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;

import static android.bluetooth.BluetoothDevice.BOND_BONDED;
import static android.bluetooth.BluetoothDevice.BOND_BONDING;
import static android.bluetooth.BluetoothDevice.BOND_NONE;
import static android.bluetooth.BluetoothGatt.GATT_SUCCESS;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_READ;
import static android.bluetooth.BluetoothProfile.GATT;

/**
 * Foreground Service that runs continuously, scanning for nearby BLE Devices, and storing IDs of other users who come in contact with the User
 **/
public class ContactScanService extends Service {

    private static final String TAG = ContactScanService.class.getSimpleName();
    public static final String CHANNEL_ID = "ContactScanServiceChannel";

    //UUIDs for the Required GATT Service and Characteristic
    private static final UUID CONTACT_SERVICE_UUID = ContactProfile.CONTACT_SERVICE_UUID;
    private static final UUID CONTACT_CHARACTERISTIC_UUID = ContactProfile.CONTACT_CHARACTERISTIC_UUID;

    public static final int REQUEST_ENABLE_BT = 1;

    //Getting an Instance of the DB
    private ContactIdDatabase contactDb = ContactIdDatabase.getInstance(this);

    public BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mBluetoothGatt;
    private Scanner_BTLE mBTLEScanner;

    //HashMap to keep a track of BLE Devices that have been found during Scanning
    private HashMap<String, BTLE_Device> mBTDevicesHashMap;

    //Queue to Handle Execution of requests to Read from a GATT Server
    private Queue<Runnable> commandQueue;
    private boolean commandQueueBusy;


    private Handler characteristicReadHandler;
    private Handler mHandler;
    private Handler scanScheduleHandler;

    private static int scanInterval = 180;//BLE Scan will run every 3 Minutes


    @Override
    public void onCreate() {
        super.onCreate();

        mBTLEScanner = new Scanner_BTLE(this,10000,-100);

        mBluetoothManager = (BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();

        mBTDevicesHashMap = new HashMap<>();


        characteristicReadHandler = new Handler();
        mHandler = new Handler();
        scanScheduleHandler = new Handler();

        commandQueue = new LinkedList<>();

        if(!checkBluetoothSupport(mBluetoothAdapter)) {
            Toast.makeText(getApplicationContext(), "Bluetooth Low Energy Not Supported.", Toast.LENGTH_SHORT).show();
        }

    }

    /**
     * Checks if Bluetooth is supported on the Device
     **/
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
                    .setContentTitle("Contact Scan Service")
                    .setContentText("Contact Scan Service Running")
                    .setContentIntent(pendingIntent)
                    .build();

        startForeground(1,notification);

        //Enable the Bluetooth Adapter, and Start the Periodic Scanning
        if(!mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.enable();
        } else {
            scanScheduleHandler.post(periodicScan);
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        stopScan();
    }

    /**Handle the BLE Devices found on scanning
     **/
    public void onDeviceFound(final BluetoothDevice device, int new_rssi) {
        String address = device.getAddress();

        if(!mBTDevicesHashMap.containsKey(address)) {
            BTLE_Device btle_device = new BTLE_Device(device);
            btle_device.setRssi(new_rssi);

            mBTDevicesHashMap.put(address,btle_device);

            mBluetoothGatt = device.connectGatt(this,false,mGattCallback);
        }
    }

    /**
     * Handle Multiple simultaneous readGATTCharacteristic Requests
     **/
    public boolean readCharacteristic(final BluetoothGattCharacteristic characteristic) {
        if(mBluetoothGatt==null) {
            Log.e(TAG, "ERROR: Gatt is null");
            return false;
        }

        if(characteristic == null) {
            Log.e(TAG, "ERROR: null Characteristic, Ignoring Read Request");
            return false;
        }

        if((characteristic.getProperties() & PROPERTY_READ) == 0) {
            Log.e(TAG, "ERROR: Characteristic cannot be read");
            return false;
        }

        boolean result = commandQueue.add(new Runnable() {
            @Override
            public void run() {
                if(!mBluetoothGatt.readCharacteristic(characteristic)) {
                    Log.e(TAG, "Read Characteristic failed");
                    completedCommand();
                }
            }
        });

        if(result) {
            nextCommand();
        } else {
            Log.e(TAG, "Error in Enqueueing new Command");
        }

        return result;
    }

    /**
     * Execute the Next Command in the Command Queue
     **/
    private void nextCommand() {
        if(commandQueueBusy) {
            return;
        }

        if(mBluetoothGatt==null) {
            Log.e(TAG, "nextCommand: Error. Null Gatt" );
            commandQueue.clear();
            commandQueueBusy = false;
            return;
        }

        if(commandQueue.size()>0) {
            final Runnable bluetoothCommand = commandQueue.peek();
            commandQueueBusy = true;

            characteristicReadHandler.post(new Runnable() {
                @Override
                public void run() {
                    try{
                        bluetoothCommand.run();
                    } catch (Exception e) {
                        Log.e(TAG, "Command Exception",e);
                    }
                }
            });
        }
    }

    private void completedCommand() {
        commandQueueBusy = false;
        commandQueue.poll();
        nextCommand();
    }

    private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        //Check Connection Status, and if Device is Connected, Request Larger MTU(Required for reading the User ID)
        public void onConnectionStateChange(final BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (status == GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    int bondState = gatt.getDevice().getBondState();
                    if (bondState == BOND_NONE || bondState == BOND_BONDED) {
                        // Connected to device, now proceed to discover it's services but delay a bit if needed
                        int delayWhenBonded = 1000;
                        final int delay = bondState == BOND_BONDED ? delayWhenBonded : 0;

                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                boolean result = gatt.requestMtu(30);
                                if (!result) {
                                    Log.e(TAG, "Failed MTU Request");
                                }
                            }
                        }, delay);
                    } else if (bondState == BOND_BONDING) {
                        Log.i(TAG, "Waiting for Bonding to COMplete");
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    gatt.close();
                } else {
                    gatt.close();
                }
            } else {
                gatt.close();
            }
        }

        @Override
        public void onMtuChanged(final BluetoothGatt gatt, int mtu, int status) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    boolean result = gatt.discoverServices();
                    if(!result) {
                        Log.e(TAG, "Service Discovery Failed");
                    }
                }
            });
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if(status==129) {
                gatt.close();
                return;
            }

            //Find the Service with the required UUID, and make a Read Request for the desired Characteristic
            final BluetoothGattCharacteristic characteristic = gatt.getService(CONTACT_SERVICE_UUID).getCharacteristic(CONTACT_CHARACTERISTIC_UUID);
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    readCharacteristic(characteristic);
                }
            });
        }

        //TODO: Check if putting close() or disconnect() is necessary for smoother running
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);

            if(status!=GATT_SUCCESS) {
                Log.e(TAG, "ERROR: Read failed");
                completedCommand();
                return;
            }
            //Get the data read from the characteristic
            final String data = characteristic.getStringValue(0);

            /*Insert a new Database Entry with the ID Just read*/
            ContactID contactID = new ContactID(data);
            ContactID[] dbInsertArgs = {contactID};

            new dbInsert().execute(dbInsertArgs);

            completedCommand();
        }
    };

    public void startScan() {
        mBTLEScanner.start();
    }

    public void stopScan() {
        mBTDevicesHashMap.clear();
    }

    /**
     * Periodically Scan for BLE Devices
     **/
    private Runnable periodicScan = new Runnable() {
        @Override
        public void run() {
            scanScheduleHandler.postDelayed(periodicScan,scanInterval*1000);
            startScan();
        }
    };

    /**
     * Setting up Notification channel required for Foreground Service
     **/
    private void createNotificationChannel() {
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new
                    NotificationChannel(
                            CHANNEL_ID,
                        "Contact Scan Service Channel",
                        NotificationManager.IMPORTANCE_DEFAULT);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(serviceChannel);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Async Task to handle Insertion into the Database
     **/
    private class dbInsert extends AsyncTask<ContactID,Void,Void> {
        @Override
        protected Void doInBackground(ContactID... contactIDS) {
            contactDb.contactIDDao().insertContactID(contactIDS[0]);
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
        }
    }
}
