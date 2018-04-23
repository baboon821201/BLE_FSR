/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.bluetoothlegatt;

import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class ThirtySecondMode extends Activity {
    private final static String TAG = ThirtySecondMode.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    boolean stop=true;
    boolean clear=false;
    ScrollView scrollView;
    String time, filename, l, ts1;
    String[] dataArray;
    StringBuilder s = new StringBuilder();
    File path1, file1;
    Long tsLong1, tsLong2, timeP;
    long timeDelay;
    Date startTime ,endTime;
    float i1, i2, i3, i4, iAvg;
    int j = 0;
    int k = 0;
    int m = 0;
    int a = 1;
    float x = 0;
    float t1 = 0;
    float t2 = 0;
    float timeDiff = 0;
    private Button btnScan, btnClear, btnSave;
    private TextView mConnectionState, test;
    private TextView mTimer, mCounter, mDelay;
    private String mDeviceName;
    private String mDeviceAddress;
    private ProgressBar DataUploadProgress;
    private TextView uploadInfoText;

    private ExpandableListView mGattServicesList;
    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";
    private BluetoothGattCharacteristic FSR;

    private StorageReference mStorageRef, dataRef;

    private LineChart mChart;
    private Thread thread;
    private boolean plotData = true;
    protected Typeface mTfRegular;
    protected Typeface mTfLight;

    //private FirebaseFirestore db = FirebaseFirestore.getInstance();

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            }
        }
    };

    // If a given GATT characteristic is selected, check for supported features.  This sample
    // demonstrates 'Read' and 'Notify' features.  See
    // http://d.android.com/reference/android/bluetooth/BluetoothGatt.html for the complete
    // list of supported characteristic features.
    /*
    private final ExpandableListView.OnChildClickListener servicesListClickListner =
            new ExpandableListView.OnChildClickListener() {
                @Override
                public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
                                            int childPosition, long id) {
                    if (mGattCharacteristics != null) {
                        final BluetoothGattCharacteristic characteristic =
                                mGattCharacteristics.get(groupPosition).get(childPosition);

                        final int charaProp = characteristic.getProperties();

                        //
                        test.setText(characteristic.getUuid()+" \\ "+charaProp);

                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                            // If there is an active notification on a characteristic, clear
                            // it first so it doesn't update the data field on the user interface.
                            if (mNotifyCharacteristic != null) {
                                mBluetoothLeService.setCharacteristicNotification(
                                        mNotifyCharacteristic, false);
                                mNotifyCharacteristic = null;
                            }
                            mBluetoothLeService.readCharacteristic(characteristic);
                        }
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                            mNotifyCharacteristic = characteristic;
                            mBluetoothLeService.setCharacteristicNotification(
                                    characteristic, true);
                        }
                        return true;
                    }
                    return false;
                }
    };
    */
    private void clearUI() {
        //mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);
        //mDataField.setText(R.string.no_pressure);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //改
        setContentView(R.layout.thirty_second_mode);
        //Environment.getDataDirectory();
        Log.d("TAG", "file path = " + Environment.getDownloadCacheDirectory());
        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        //改
        // Sets up UI references.

        //((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        //mGattServicesList = (ExpandableListView) findViewById(R.id.gatt_services_list);
        //mGattServicesList.setOnChildClickListener(servicesListClickListner);
        //mConnectionState = (TextView) findViewById(R.id.connection_state);
        //mDataField = (TextView)findViewById(R.id.alldata);
        // scrollView = (ScrollView)findViewById(R.id.sv1);
        //test=(TextView)findViewById(R.id.TextView0);

        initData();
        initView();

        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    private void initData(){
        mStorageRef = FirebaseStorage.getInstance().getReference();
    }


    private void initView(){
        //mTime = (TextView) findViewById(R.id.showTime);
        //mS1 = (TextView)findViewById(R.id.showS1);
        //mS2 = (TextView)findViewById(R.id.showS2);
        //mS3 = (TextView)findViewById(R.id.showS3);
        //mS4 = (TextView)findViewById(R.id.showS4);
        //mAvg = (TextView)findViewById(R.id.showAvg);

        mTimer = (TextView)findViewById(R.id.timer);
        mCounter = (TextView)findViewById(R.id.counter);
        mDelay = (TextView)findViewById(R.id.time_delay);
        scrollView = (ScrollView)findViewById(R.id.sv1);

        btnScan=(Button)findViewById(R.id.scan1);
        btnScan.setOnClickListener(StartScanClickListener);
        btnSave=(Button)findViewById(R.id.save1);
        btnSave.setEnabled(false);
        btnSave.setOnClickListener(SaveDataClickListener);
        btnClear = (Button)findViewById(R.id.clear1);
        btnClear.setEnabled(false);
        btnClear.setOnClickListener(ClearClickListener);


        mChart = (LineChart)findViewById(R.id.chart1);

        // enable description text
        mChart.getDescription().setEnabled(true);
        mChart.getDescription().setText("Real Time Pressure");

        // enable touch gestures
        mChart.setTouchEnabled(true);

        // enable scaling and dragging
        mChart.setDragEnabled(true);
        mChart.setScaleEnabled(true);
        mChart.setDrawGridBackground(false);

        // if disabled, scaling can be done on x- and y-axis separately
        mChart.setPinchZoom(true);

        // set an alternative background color
        mChart.setBackgroundColor(Color.LTGRAY);

        LineData data = new LineData();
        data.setValueTextColor(Color.WHITE);

        // add empty data
        mChart.setData(data);

        // get the legend (only possible after setting data)
        Legend l = mChart.getLegend();

        // modify the legend ...
        l.setForm(Legend.LegendForm.LINE);
        l.setTypeface(mTfLight);
        l.setTextColor(Color.WHITE);

        XAxis xl = mChart.getXAxis();
        xl.setTypeface(mTfLight);
        xl.setTextColor(Color.WHITE);
        xl.setDrawGridLines(false);
        xl.setAvoidFirstLastClipping(true);
        xl.setEnabled(true);

        YAxis leftAxis = mChart.getAxisLeft();
        leftAxis.setTypeface(mTfLight);
        leftAxis.setTextColor(Color.WHITE);
        leftAxis.setAxisMaximum(1000f);
        leftAxis.setAxisMinimum(0f);
        leftAxis.setDrawGridLines(true);

        YAxis rightAxis = mChart.getAxisRight();
        rightAxis.setEnabled(false);

        DataUploadProgress = (ProgressBar) findViewById(R.id.upload_progress);
        uploadInfoText = (TextView)findViewById(R.id.uploadInfoText);


    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //mConnectionState.setText(resourceId);
            }
        });
    }
    private void addEntry() {
        LineData data = mChart.getData();
        if (data != null) {

            ILineDataSet set = data.getDataSetByIndex(0);
            // set.addEntry(...); // can be called as well

            if (set == null) {
                set = createSet();
                data.addDataSet(set);
            }

            data.addEntry(new Entry(set.getEntryCount(), iAvg), 0);
            data.notifyDataChanged();

            // let the chart know it's data has changed
            mChart.notifyDataSetChanged();

            // limit the number of visible entries
            mChart.setVisibleXRangeMaximum(120);
            // mChart.setVisibleYRange(30, AxisDependency.LEFT);

            // move to the latest entry
            mChart.moveViewToX(data.getEntryCount());

            // this automatically refreshes the chart (calls invalidate())
            // mChart.moveViewTo(data.getXValCount()-7, 55f,
            // AxisDependency.LEFT);
        }
    }

    private LineDataSet createSet() {
        LineDataSet set = new LineDataSet(null, "Dynamic Data");
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setColor(ColorTemplate.getHoloBlue());
        set.setCircleColor(Color.WHITE);
        set.setLineWidth(2f);
        set.setCircleRadius(4f);
        set.setFillAlpha(65);
        set.setFillColor(ColorTemplate.getHoloBlue());
        set.setHighLightColor(Color.rgb(244, 117, 117));
        set.setValueTextColor(Color.WHITE);
        set.setValueTextSize(9f);
        set.setDrawValues(false);
        return set;
    }



    private void displayData(String data) {
        if(!stop) {
            if (data != null) {
                dataArray = data.split(",");

                //Log.d("xxxxx", dataArray[0]);

                //mTime.setText(dataArray[0]);
                //mS1.setText(dataArray[1]);
                //mS2.setText(dataArray[2]);
                //mS3.setText(dataArray[3]);
                //mS4.setText(dataArray[4]);
                //mAvg.setText(dataArray[5]);

                /*
                mTime.append(dataArray[0]+"\n");
                mS1.append(dataArray[1]+"\n");
                mS2.append(dataArray[2]+"\n");
                mS3.append(dataArray[3]+"\n");
                mS4.append(dataArray[4]+"\n");
                mAvg.append(dataArray[5]);
                */
                s.append(data);
/*
                scrollView.post(new Runnable() {
                    @Override
                    public void run() {
                        scrollView.fullScroll(View.FOCUS_DOWN);
                    }
                });
*/

                /*
                l = Integer.toString(j);
                Log.d(TAG, l);
                mAvg.setText(l);
                */
            }

            //long timeStamp1 = 0, timeStamp2 = 0;
            i1 = Float.valueOf(dataArray[1]);
            i2 = Float.valueOf(dataArray[2]);
            i3 = Float.valueOf(dataArray[3]);
            i4 = Float.valueOf(dataArray[4]);
            iAvg = Float.valueOf(dataArray[5]);

           // FirebaseFirestore db = FirebaseFirestore.getInstance();

            //Map<String, Object> thirtySecond = new HashMap<>();
            //thirtySecond.put("Time", dataArray[0]);
            //thirtySecond.put("右上", dataArray[1]);
            //thirtySecond.put("右下", dataArray[2]);
            //thirtySecond.put("左下", dataArray[3]);
            //thirtySecond.put("左上", dataArray[4]);
            //thirtySecond.put("平均", dataArray[5]);
            ////thirtySecond.put("born", 1815);
//
            //// Add a new document with a generated ID
            //db.collection("thirtySecond").document(time).collection(dataArray[0])
            //        .add(thirtySecond)
            //        .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
            //            @Override
            //            public void onSuccess(DocumentReference documentReference) {
            //                Log.d(TAG, "DocumentSnapshot added with ID: " + documentReference.getId());
            //            }
            //        })
            //        .addOnFailureListener(new OnFailureListener() {
            //            @Override
            //            public void onFailure(@NonNull Exception e) {
            //                Log.w(TAG, "Error adding document", e);
            //            }
            //        });


            if((i1>i2) && (i4>i3)){
                j=1;
                if (a == 1) {
                    startTime = new Date(System.currentTimeMillis());
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
                    String dateString = sdf.format(startTime);
                    Log.d(TAG, dateString);


                    tsLong1 = System.currentTimeMillis();
                    t1 = tsLong1.intValue();
                    String ts = tsLong1.toString();
                    //Log.d(TAG, ts);
                    a=0;
                }

            }
            if(iAvg==0.0){
                if(j==1){
                    k++;
                    j = 0;
                    endTime = new Date(System.currentTimeMillis());
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
                    String dateString = sdf.format(endTime);
                    Log.d(TAG, dateString);

                    timeDelay = endTime.getTime() - startTime.getTime();

                    x = (float)timeDelay / 1000;

                    String diff = String.valueOf(x);
                    Log.d(TAG, diff);
/*
                    //String str = String.valueOf(timeDelay);
                    tsLong2 = System.currentTimeMillis();
                    t2 = tsLong2.intValue();
                    String ts = tsLong2.toString();
                    //Log.d(TAG, ts);
                    a=1;

                    timeDiff = (t2 - t1)/1000;
                    ts1 = Float.toString(timeDiff);
                    //DecimalFormat mDecimalFormat = new DecimalFormat("#.###");
                    //String ts2 = mDecimalFormat.format(Double.parseDouble(ts1));
                    //Log.d(TAG, ts1);
*/
                    m=m+k;
                    l = Integer.toString(m);
                    mCounter.append(l + "\n");
                    mDelay.append(diff + "\n");
                    scrollView.post(new Runnable() {
                        @Override
                        public void run() {
                            scrollView.fullScroll(View.FOCUS_DOWN);
                        }
                    });

                    k=0;
                    a=1;
                }
            }



            //timeP = (tsLong2 - tsLong1);

            //m=m+k;
            //Long tsLong = System.currentTimeMillis();

            //l = Integer.toString(m);
            //Log.d(TAG, l);
            //mCounter.setText(l);
            //mDelay.setText(ts1);

            //mCounter.append(l+"\n");
            //mCounter.append(ts1+"\n");
            //scrollView.post(new Runnable() {
            //    @Override
            //    public void run() {
            //        scrollView.fullScroll(View.FOCUS_DOWN);
            //    }
            //});


            //k=0;
        }
    }

    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.


    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<ArrayList<HashMap<String, String>>>();
        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(
                    LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                    new ArrayList<HashMap<String, String>>();
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<BluetoothGattCharacteristic>();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<String, String>();
                uuid = gattCharacteristic.getUuid().toString();

                //find which uuid we want
                if(uuid.equals("6e400003-b5a3-f393-e0a9-e50e24dcca9e")){
                    //test.setText(uuid);
                    FSR=gattCharacteristic;
                }

                currentCharaData.put(
                        LIST_NAME, SampleGattAttributes.lookup(uuid, unknownCharaString));
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);
            }
            mGattCharacteristics.add(charas);
            gattCharacteristicData.add(gattCharacteristicGroupData);
        }

        SimpleExpandableListAdapter gattServiceAdapter = new SimpleExpandableListAdapter(
                this,
                gattServiceData,
                android.R.layout.simple_expandable_list_item_2,
                new String[] {LIST_NAME, LIST_UUID},
                new int[] { android.R.id.text1, android.R.id.text2 },
                gattCharacteristicData,
                android.R.layout.simple_expandable_list_item_2,
                new String[] {LIST_NAME, LIST_UUID},
                new int[] { android.R.id.text1, android.R.id.text2 }
        );
        //mGattServicesList.setAdapter(gattServiceAdapter);
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    private CountDownTimer timer = new CountDownTimer(30000, 1)
     {
         @Override
        public void onTick(long millisUntilFinished) {
             mTimer.setText( String.valueOf(millisUntilFinished / 1000));
        }
         @Override
        public void onFinish() {
            //mTimer.setText("0.000");
             btnScan.setText("Start Scan");
             btnScan.setEnabled(false);
             btnClear.setEnabled(true);
             btnSave.setEnabled(true);
             stop = true;
        }
    };

    //按下button時要做的事情
    public Button.OnClickListener StartScanClickListener = new Button.OnClickListener() {
        @Override
        public void onClick(View v) {
            Button b = (Button) v;
            if(b.getText().equals("Start Scan")){
                SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd_HH_mm_ss");
                Calendar c = Calendar.getInstance();
                String t = df.format(c.getTime());
                time = t;
                if (mGattCharacteristics != null) {
                    final BluetoothGattCharacteristic characteristic =FSR;
                    final int charaProp = characteristic.getProperties();
                    if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                        // If there is an active notification on a characteristic, clear
                        // it first so it doesn't update the data field on the user interface.
                        if (mNotifyCharacteristic != null) {
                            mBluetoothLeService.setCharacteristicNotification(
                                    mNotifyCharacteristic, false);
                            mNotifyCharacteristic = null;
                        }
                        mBluetoothLeService.readCharacteristic(characteristic);
                    }
                    if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                        mNotifyCharacteristic = characteristic;
                        mBluetoothLeService.setCharacteristicNotification(
                                characteristic, true);
                    }
                }

                timer.start();
                //feedMultiple();

                if (thread != null)
                    thread.interrupt();
                final Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        addEntry();
                    }
                };

                thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        do {
                            // Don't generate garbage runnables inside the loop.
                            runOnUiThread(runnable);
                            try {
                                Thread.sleep(10);
                            } catch (InterruptedException e) {
                                //  Auto-generated catch block
                                e.printStackTrace();
                            }

                        }while (!stop);
                    }
                });
                thread.start();

                b.setText("Stop Scan");
                stop = false;

            }else{
                //mDataField.setText("No Pressure");
                timer.cancel();
                stop = true;
                b.setText("Start Scan");
                btnScan.setEnabled(false);
                btnClear.setEnabled(true);
                btnSave.setEnabled(true);
            }
        }
    };

    public Button.OnClickListener ClearClickListener = new Button.OnClickListener() {
        @Override
        public void onClick(View v) {
            Button b = (Button) v;
            if(b.getText().equals("Clear Previous Data")){
                //mTime.setText("");
                //mS1.setText("");
                //mS2.setText("");
                //mS3.setText("");
                //mS4.setText("");
                //mAvg.setText("");
                mTimer.setText("30");
                mCounter.setText("");
                mDelay.setText("");
                //s.setLength(0);
                j = 0;
                k = 0;
                m = 0;
                clear=true;
                btnScan.setEnabled(true);
                btnSave.setEnabled(false);
                btnClear.setEnabled(false);

                mChart.clearValues();

                uploadInfoText.setText("");
            }
        }
    };


    public Button.OnClickListener SaveDataClickListener = new Button.OnClickListener() {
        @Override
        public void onClick(View v) {
            Button b = (Button) v;
            if(b.getText().equals("Save Data")){
                boolean hasExternalStorage = isExternalStorageWritable();
                if(hasExternalStorage){
                    filename = time + ".csv";
                    Log.d(TAG, "filename = " + filename);

                    path1 = Environment.getExternalStoragePublicDirectory("/FSR/Thirty Second Mode/");
                    file1 = new File(path1, filename);
                    Log.d(TAG, "path = " + path1);

                    try {
                        path1.mkdirs();
                        OutputStream outputStream = new FileOutputStream(file1, true);
                        //String s1 = "Time" + ",\t\t" + "sensor1" + ",\t" + "sensor2" + ",\t" + "sensor3" + ",\t" + "sensor4" + ",\t" + "Average" + ",\n";
                        String s1 = "Time" + "," + "Right Front" + "," + "Right Rear"+ "," + "Left Rear"+ "," + "Left Front"+ "," + "Average" + "\n";
                        s.toString();
                        //String dataArrayString = saveData;
                        String all = s1 + s;
                        outputStream.write(all.getBytes());
                        outputStream.close();
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        Log.w("ExternalStorage", "Error writing " + file1, e);
                    }
                    //Toast.makeText(DeviceControlActivity.this, "Save in:" + path  + "/"+ filename, Toast.LENGTH_LONG).show();
                }
                else{
                    Toast.makeText(ThirtySecondMode.this, "no storage", Toast.LENGTH_LONG).show();
                }

                /*
                String s1 = "Time" + "\t" + "sensor1" + "\t" + "sensor2" + "\t" + "sensor3" + "\t" + "sensor4" + "\t" + "Average" + "\n";
                String s2 = mTime + "\t" + mS1 + "\t" + mS2 + "\t" + mS3 + "\t" + mS4 + "\t" + mAvg;
                String all = s1 + s2;
                            */
                String name = filename.toString();
                String filePath = Environment.getExternalStorageDirectory().toString() + "/FSR/Thirty Second Mode/" + name;

                uploadData(filePath);

                Toast.makeText(ThirtySecondMode.this, "Save in:" + filePath, Toast.LENGTH_LONG).show();
                DataUploadProgress.setVisibility(View.VISIBLE);
                btnSave.setEnabled(false);
            }
        }
    };


    public boolean isExternalStorageWritable(){
        String state = Environment.getExternalStorageState();
        if(Environment.MEDIA_MOUNTED.equals(state)){
            return true;
        }
        return false;
    }

    private void uploadData(String path){
        Uri file = Uri.fromFile(new File(path));
        StorageMetadata metadata = new StorageMetadata.Builder()
                .setContentType("data/csv")
                .build();
        dataRef = mStorageRef.child("Thirty Second Mode/" + filename);
        UploadTask uploadTask = dataRef.putFile(file, metadata);
        uploadTask.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception exception) {
                uploadInfoText.setText(exception.getMessage());
            }
        }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                uploadInfoText.setText(R.string.up_load_success);
            }
        }).addOnProgressListener(new OnProgressListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onProgress(UploadTask.TaskSnapshot taskSnapshot) {
                int progress = (int)((100.0 * taskSnapshot.getBytesTransferred()) / taskSnapshot.getTotalByteCount());
                DataUploadProgress.setProgress(progress);
                if(progress >= 100){
                    DataUploadProgress.setVisibility(View.GONE);
                }
            }
        });
    }
}
