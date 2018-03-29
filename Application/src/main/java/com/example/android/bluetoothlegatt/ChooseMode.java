package com.example.android.bluetoothlegatt;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class ChooseMode extends Activity {
    private Button btnGeneral, btn30Second;
    private String mDeviceName;
    private String mDeviceAddress;

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();
        setContentView(R.layout.choose_mode);
        initView();

        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        btnGeneral.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                deliverToGeneral();
            }
        });

        btn30Second.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                deliverToThirtySecond();
            }
        });

    }

    private void initView(){
        btnGeneral = (Button)findViewById(R.id.generalScan);
        btn30Second = (Button)findViewById(R.id.thirtySecond);
    }

    public void deliverToGeneral(){
        Bundle bundle = getIntent().getExtras();
        Intent intent = new Intent();
        intent.setClass(this, DeviceControlActivity.class);
        intent.putExtras(bundle);
        startActivity(intent);
    }

    public void deliverToThirtySecond(){
        Bundle bundle = getIntent().getExtras();
        Intent intent = new Intent();
        intent.setClass(this, ThirtySecondMode.class);
        intent.putExtras(bundle);
        startActivity(intent);
    }



}
