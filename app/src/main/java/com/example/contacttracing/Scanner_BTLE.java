package com.example.contacttracing;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Class to Handle Scanning of BLE Devices
 */
public class Scanner_BTLE {

    private ContactScanService contactScanService;
    private MainActivity mainActivity;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothLeScanner;
    private boolean mScanning;
    private long scanPeriod;
    private int signalStrength;

    private Handler mHandler;

    private static final UUID CONTACT_SERVICE_UUID = ContactProfile.CONTACT_SERVICE_UUID;
    private static final UUID CONTACT_CHARACTERISTIC_UUID = ContactProfile.CONTACT_CHARACTERISTIC_UUID;

    public Scanner_BTLE(ContactScanService service, long scanPeriod, int signalStrength) {
        this.contactScanService = service;
        this.scanPeriod = scanPeriod;
        this.signalStrength = signalStrength;
        mHandler = new Handler();

        final BluetoothManager bluetoothManager = (BluetoothManager)contactScanService.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
    }

    public boolean isScanning() {
        return mScanning;
    }

    public void start() {
        if(!Utils.checkBluetooth(mBluetoothAdapter)) {
            Utils.requestUserBluetooth(mainActivity);
        } else {
            scanLeDevice(true);
        }
    }

    public void stop() {
        scanLeDevice(false);
    }

    private void scanLeDevice(final boolean enable) {
        //Set a Delayed Command to Stop the BLE Scan(After the Desired Scan Period)
        if(enable&&!mScanning) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mBluetoothLeScanner.stopScan(mLeScanCallback);
                    contactScanService.stopScan();
                }
            },scanPeriod);
        }

        mScanning = true;
        //Set the Filter for the BLE Scan, to connect only to devices which have the desired service
        ScanFilter leScanFilter = new ScanFilter.Builder().setServiceUuid(new ParcelUuid(ContactProfile.CONTACT_SERVICE_UUID)).build();
        ScanSettings leScanSettings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build();

        List<ScanFilter> scanFilters = new ArrayList<ScanFilter>();
        scanFilters.add(leScanFilter);

        mBluetoothLeScanner.startScan(scanFilters,leScanSettings,mLeScanCallback);
    }

    private ScanCallback mLeScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            final int new_rssi = result.getRssi();
            if(new_rssi > signalStrength) {
                //Pass on the device to the Contact Scan Service
                contactScanService.onDeviceFound(result.getDevice(),new_rssi);
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for(ScanResult result : results) {
                contactScanService.onDeviceFound(result.getDevice(),result.getRssi());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
        }
    };
}


