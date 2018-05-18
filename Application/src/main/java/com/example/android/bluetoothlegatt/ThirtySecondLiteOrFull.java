package com.example.android.bluetoothlegatt;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class ThirtySecondLiteOrFull  extends Activity {
    private Button btn30Lite, btn30Full;

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.thirty_lite_or_full);
        initView();
        btn30Lite.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                deliverTo30Lite();
            }
        });

        btn30Full.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                deliverTo30Full();
            }
        });
    }

    private void initView(){
        btn30Lite = (Button)findViewById(R.id.liteVersion);
        btn30Full = (Button)findViewById(R.id.fullVersion);
    }

    public void deliverTo30Lite(){
        Bundle bundle = getIntent().getExtras();
        Intent intent = new Intent();
        intent.setClass(this, ThirtySecondLite.class);
        assert bundle != null;
        intent.putExtras(bundle);
        startActivity(intent);
    }

    public void deliverTo30Full(){
        Bundle bundle = getIntent().getExtras();
        Intent intent = new Intent();
        intent.setClass(this, ThirtySecondMode.class);
        assert bundle != null;
        intent.putExtras(bundle);
        startActivity(intent);
    }

}
