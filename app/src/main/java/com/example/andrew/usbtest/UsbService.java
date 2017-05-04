package com.example.andrew.usbtest;

import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

public class UsbService extends Service {
    private static final String TAG = "UsbService";
    private static final String ACTION_USB_PERMISSION = UsbService.class.getPackage().getName() + ".ACTION_USB_PERMISSION";
    String fileName = "sensor-data.txt";
    ArrayList<String> dataBuffer = new ArrayList<>();
    private BroadcastReceiver broadcastReceiver;
    private UsbManager usbManager;
    private UsbSerialDevice serial;
    private Disposable disposable;

    public UsbService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();

        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                Log.d(TAG, "onReceive: " + intent);
                Log.d(TAG, "onReceive: " + device);
                if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
                    UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (usbManager.hasPermission(usbDevice)) {
                        handleDeviceAttached(usbDevice);
                    }
                } else if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_DETACHED)) {
                    handleDeviceDetached(intent.getParcelableExtra(UsbManager.EXTRA_DEVICE));
                }
            }
        };

        IntentFilter intentFilter = new IntentFilter();

        intentFilter.addAction(ACTION_USB_PERMISSION);
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);

        registerReceiver(broadcastReceiver, intentFilter);
    }

    private void handleDeviceAttached(UsbDevice usbDevice) {
        Log.d(TAG, "handleDeviceAttached: " + usbDevice);

        UsbDeviceConnection usbConnection = usbManager.openDevice(usbDevice);
        serial = UsbSerialDevice.createUsbSerialDevice(usbDevice, usbConnection);

        serial.open();
        serial.setBaudRate(115200);
        serial.setDataBits(UsbSerialInterface.DATA_BITS_8);
        serial.setParity(UsbSerialInterface.PARITY_ODD);
        serial.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);

        serial.read(bytes -> {
            Log.d(TAG, "onReceivedData: " + new String(bytes, StandardCharsets.UTF_8));

            String receivedData = new String(bytes, StandardCharsets.UTF_8);
            if (receivedData.contains("1,PPG PR")) {
                dataBuffer.add(receivedData);
            } else if (receivedData.contains("1,SPO2,")) {
                dataBuffer.add(receivedData);
            }

            if (dataBuffer.size() >= 2) {
                writeBufferToFile();
            }

            dataBuffer.clear();
        });

        disposable = Observable.interval(3, TimeUnit.SECONDS)
                .flatMapCompletable(aLong -> {
                    Log.d(TAG, "Sending start");
                    serial.write("START\n".getBytes(StandardCharsets.UTF_8));
                    return Completable.complete();
                }).subscribe(() -> {

                }, throwable -> Log.e(TAG, "accept: ", throwable));
    }

    private void handleDeviceDetached(UsbDevice usbDevice) {
        Log.d(TAG, "handleDeviceDetached: " + usbDevice);

        if (serial != null) {
            disposable.dispose();
            serial.close();
            //noinspection ResultOfMethodCallIgnored
            getOutputFile().delete();
        }

        serial = null;
    }

    private void writeBufferToFile() {


        StringBuilder dataString = new StringBuilder();
        for (String s : dataBuffer) {
            dataString.append(s).append("\n");
        }

        try {
            FileUtils.writeByteArrayToFile(getOutputFile(), dataString.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Log.e(TAG, "Failed to write to file: ", e);
        }
    }

    private File getOutputFile() {
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    private void requestPermission(UsbDevice usbDevice) {
        Log.d(TAG, "requestPermission: ");
        Intent intent = new Intent(ACTION_USB_PERMISSION);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0);

        usbManager.requestPermission(usbDevice, pendingIntent);
    }
}
