package com.example.android.bluetoothlegatt;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class ChooseMode extends Activity {
    private Button btnGeneral, btn30Second;

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.choose_mode);
        initView();
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
        assert bundle != null;
        intent.putExtras(bundle);
        startActivity(intent);
    }

    public void deliverToThirtySecond(){
        Bundle bundle = getIntent().getExtras();
        Intent intent = new Intent();
        intent.setClass(this, ThirtySecondLiteOrFull.class);
        assert bundle != null;
        intent.putExtras(bundle);
        startActivity(intent);
    }
}
