package com.kimmiro.app;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Local (app-module) plugins are not auto-registered; register before super.onCreate().
        registerPlugin(UpdaterPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
