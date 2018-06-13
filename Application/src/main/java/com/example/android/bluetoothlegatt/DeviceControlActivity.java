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
import android.app.Service;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Vibrator;
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

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.ubidots.ApiClient;
import com.ubidots.Variable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;


/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    private final String API_KEY = "A1E-a681b156def69c266fdfa1a95e82c6a67c6b";
    private final String VARIABLE_ID1 = "5ae97900c03f973cd554f1b9";
    private final String VARIABLE_ID2 = "5af3dee8c03f977f6bb4fcab";
    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    boolean stop=true;
    boolean clear=false;
    ScrollView scrollView;
    String time, filename;
    String[] dataArray;
    StringBuilder s = new StringBuilder();
    File path, file;
    float avg ;
    float topRight=0, buttomRight=0, buttomLeft=0, topLeft=0, iAvg=0;
    float tL_bL=0, tR_bR=0, tL_tR=0, bL_bR=0;
    private Button btnScan, btnClear, btnSave;
    private TextView mConnectionState, test;
    private TextView mTime, mS1, mS2, mS3, mS4, mAvg, mDataField, mShowAlert;
    private String mDeviceName;
    private String mDeviceAddress;
    private ProgressBar DataUploadProgress;
    private TextView uploadInfoText;
    private Thread thread;

    TimerTask doAsynchronousTask;
    Timer timer, timer1;
    final Handler handler = new Handler();

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
        setContentView(R.layout.scan_clear_save_button);
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
        mTime = (TextView) findViewById(R.id.showTime);
        mS1 = (TextView)findViewById(R.id.showS1);
        mS2 = (TextView)findViewById(R.id.showS2);
        mS3 = (TextView)findViewById(R.id.showS3);
        mS4 = (TextView)findViewById(R.id.showS4);
        mAvg = (TextView)findViewById(R.id.showAvg);
        mShowAlert = (TextView)findViewById(R.id.showAlert);


        btnScan=(Button)findViewById(R.id.scan);
        btnScan.setOnClickListener(StartScanClickListener);
        btnSave=(Button)findViewById(R.id.save);
        btnSave.setEnabled(false);
        btnSave.setOnClickListener(SaveDataClickListener);
        btnClear = (Button)findViewById(R.id.clear);
        btnClear.setEnabled(false);
        btnClear.setOnClickListener(ClearClickListener);

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

    private void displayData(String data) {
        if(!stop) {
            if (data != null) {
                dataArray = data.split(",");

                mTime.setText(dataArray[0]);
                mS1.setText(dataArray[1]);
                mS2.setText(dataArray[2]);
                mS3.setText(dataArray[3]);
                mS4.setText(dataArray[4]);
                mAvg.setText(dataArray[5]);


                topRight = Float.valueOf(dataArray[1]);
                buttomRight = Float.valueOf(dataArray[2]);
                buttomLeft = Float.valueOf(dataArray[3]);
                topLeft = Float.valueOf(dataArray[4]);
                iAvg = Float.valueOf(dataArray[5]);

                //tL_bL=0, tR_bR=0, tL_tR=0, bR_bL=0

                //Left
                tL_bL = topLeft + buttomLeft;

                //Right
                tR_bR = topRight + buttomRight;

                //Top
                tL_tR = topLeft + topRight;

                //Buttom
                bL_bR = buttomLeft + buttomRight;


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

                //avg = Float.valueOf(dataArray[5]);
                //new ApiUbidots().execute(avg);



            }


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
                callAsynchronousTask();
                //feedMultiple();

                //tL_bL=0, tR_bR=0, tL_tR=0, bL_bR=0

                final Handler handler = new Handler();
                timer1 = new Timer();
                doAsynchronousTask = new TimerTask() {
                    @Override
                    public void run() {
                        handler.post(new Runnable() {
                            public void run() {
                                try {
                                    /*
                                    if(tL_bL>1650.0 && tR_bR<1650.0 && tL_tR<1650.0 && bL_bR>1650.0 && iAvg<850.0){
                                        mShowAlert.setText("Too Much Pressure On Left!");
                                        Vibrator myVibrator = (Vibrator) getApplication().getSystemService(Service.VIBRATOR_SERVICE);
                                        myVibrator.vibrate(500);
                                    }else if(tL_bL<1650.0 && tR_bR>1650.0 && tL_tR<1650.0 && bL_bR>1650.0 && iAvg<850.0){
                                        mShowAlert.setText("Too Much Pressure On Right!");
                                        Vibrator myVibrator = (Vibrator) getApplication().getSystemService(Service.VIBRATOR_SERVICE);
                                        myVibrator.vibrate(500);
                                    }else if(tL_bL>1650.0 && tR_bR>1650.0 && tL_tR>1650.0 && bL_bR>1650.0 && iAvg>850.0){
                                        mShowAlert.setText("Too Much Pressure On Front!");
                                        Vibrator myVibrator = (Vibrator) getApplication().getSystemService(Service.VIBRATOR_SERVICE);
                                        myVibrator.vibrate(500);
                                    }else if(tL_bL>1650.0 && tR_bR>1650.0 && tL_tR<1650.0 && bL_bR>1650.0 && iAvg<850.0){
                                        mShowAlert.setText("Too Much Pressure On Back!");
                                        Vibrator myVibrator = (Vibrator) getApplication().getSystemService(Service.VIBRATOR_SERVICE);
                                        myVibrator.vibrate(500);
                                    }else{
                                        mShowAlert.setText(" ");
                                    }
                                    */
                                    if(topLeft>830.0 && topRight<830.0 && (buttomLeft>830.0 || buttomRight>830.0) && iAvg<850.0){
                                        mShowAlert.setText("Too Much Pressure On Left!");
                                        Vibrator myVibrator = (Vibrator) getApplication().getSystemService(Service.VIBRATOR_SERVICE);
                                        myVibrator.vibrate(500);
                                    }else if(topLeft<830.0 && topRight>830.0 && (buttomLeft>830.0 || buttomRight>830.0) && iAvg<850.0){
                                        mShowAlert.setText("Too Much Pressure On Right!");
                                        Vibrator myVibrator = (Vibrator) getApplication().getSystemService(Service.VIBRATOR_SERVICE);
                                        myVibrator.vibrate(500);
                                    }else if(topLeft>830.0 && topRight>830.0 && buttomLeft>830.0 && buttomRight>830.0 && iAvg>850.0){
                                        mShowAlert.setText("Too Much Pressure On Front!");
                                        Vibrator myVibrator = (Vibrator) getApplication().getSystemService(Service.VIBRATOR_SERVICE);
                                        myVibrator.vibrate(500);
                                    }else if(topLeft<830.0 && topRight<830.0 && buttomLeft>830.0 && buttomRight>830.0 && iAvg<850.0){
                                        mShowAlert.setText("Too Much Pressure On Back!");
                                        Vibrator myVibrator = (Vibrator) getApplication().getSystemService(Service.VIBRATOR_SERVICE);
                                        myVibrator.vibrate(500);
                                    }else{
                                        mShowAlert.setText(" ");
                                    }
                                } catch (Exception e) {
                                    android.util.Log.i("Error", "Error");
                                    // TODO Auto-generated catch block
                                }
                            }
                        });
                    }
                };
                timer1.schedule(doAsynchronousTask, 0, 2500);

                b.setText("Stop Scan");
                stop = false;
            }else{
                //mDataField.setText("No Pressure");
                stop = true;
                timer.cancel();
                timer.purge();
                handler.removeCallbacksAndMessages(null);
                b.setText("Start Scan");
                btnScan.setEnabled(false);
                btnClear.setEnabled(true);
                btnSave.setEnabled(true);
                timer1.cancel();
            }
        }
    };

    public Button.OnClickListener ClearClickListener = new Button.OnClickListener() {
        @Override
        public void onClick(View v) {
            Button b = (Button) v;
            if(b.getText().equals("Clear Previous Data")){
                mTime.setText("");
                mS1.setText("");
                mS2.setText("");
                mS3.setText("");
                mS4.setText("");
                mAvg.setText("");
                s.setLength(0);
                clear=true;
                btnScan.setEnabled(true);
                btnSave.setEnabled(false);
                btnClear.setEnabled(false);

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

                    path = Environment.getExternalStoragePublicDirectory("/FSR/General Scan Mode/");
                    file = new File(path, filename);
                    Log.d(TAG, "path = " + path);

                    try {
                        path.mkdirs();
                        OutputStream outputStream = new FileOutputStream(file, true);
                        //String s1 = "Time" + ",\t\t" + "sensor1" + ",\t" + "sensor2" + ",\t" + "sensor3" + ",\t" + "sensor4" + ",\t" + "Average" + ",\n";
                        String s1 = "Time" + "," + "Top Right" + "," + "Buttom Right"+ "," + "Buttom Left"+ "," + "Top Left"+ "," + "Average" + "\n";
                        s.toString();
                        //String dataArrayString = saveData;
                        String all = s1 + s;
                        outputStream.write(all.getBytes());
                        outputStream.close();
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        Log.w("ExternalStorage", "Error writing " + file, e);
                    }
                    //Toast.makeText(DeviceControlActivity.this, "Save in:" + path  + "/"+ filename, Toast.LENGTH_LONG).show();
                }
                else{
                    Toast.makeText(DeviceControlActivity.this, "no storage", Toast.LENGTH_LONG).show();
                }

                /*
                String s1 = "Time" + "\t" + "sensor1" + "\t" + "sensor2" + "\t" + "sensor3" + "\t" + "sensor4" + "\t" + "Average" + "\n";
                String s2 = mTime + "\t" + mS1 + "\t" + mS2 + "\t" + mS3 + "\t" + mS4 + "\t" + mAvg;
                String all = s1 + s2;
                            */
                String name = filename.toString();
                String filePath = Environment.getExternalStorageDirectory().toString() + "/FSR/General Scan Mode/" + name;

                uploadData(filePath);

                Toast.makeText(DeviceControlActivity.this, "Save in:" + filePath, Toast.LENGTH_LONG).show();
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
        dataRef = mStorageRef.child("General Mode/" + filename);
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

    public class ApiUbidots extends AsyncTask<Float, Void, Void[]> {
        @Override
        protected Void[] doInBackground(Float... params) {
            ApiClient apiClient = new ApiClient(API_KEY);

            Variable pressure = apiClient.getVariable(VARIABLE_ID1);
            //Variable test = apiClient.getVariable(VARIABLE_ID2);

            pressure.saveValue(params[0]);
            //test.saveValue(params[0]);
            return null;
        }
    }

    public void callAsynchronousTask() {
        final Handler handler = new Handler();
        timer = new Timer();
        doAsynchronousTask = new TimerTask() {
            @Override
            public void run() {
                handler.post(new Runnable() {
                    public void run() {
                        try {
                            ApiUbidots apiUbidots = new ApiUbidots();
                            apiUbidots.execute(iAvg);


                        } catch (Exception e) {
                            android.util.Log.i("Error", "Error");
                            // TODO Auto-generated catch block
                        }
                    }
                });
            }
        };
        timer.schedule(doAsynchronousTask, 0, 2000);
    }
}
