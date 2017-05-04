package com.example.andrew.usbtest;

import android.app.Application;
import android.content.Intent;

public class MainApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        startService(new Intent(this, UsbService.class));
    }
}
