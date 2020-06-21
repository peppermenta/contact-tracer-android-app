package com.example.contacttracing;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;

import java.util.UUID;

/**
 * Profile for the Contact Scan GATT Service
 **/
public class ContactProfile {
    private static final String Tag = ContactProfile.class.getSimpleName();

    public static final UUID CONTACT_SERVICE_UUID = UUID.fromString("f593878e-bfbf-4539-875d-a31a6dc1cd6d");
    public static final UUID CONTACT_CHARACTERISTIC_UUID = UUID.fromString("2feeee41-7609-4918-b63d-827015d932e2");

    //Creates the New Service, with the required Characteristics
    public static BluetoothGattService createContactService() {
        BluetoothGattService contactService = new BluetoothGattService(CONTACT_SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);

        BluetoothGattCharacteristic contactCharacteristic = new BluetoothGattCharacteristic(CONTACT_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ);

        contactService.addCharacteristic(contactCharacteristic);

        return contactService;
    }


}
