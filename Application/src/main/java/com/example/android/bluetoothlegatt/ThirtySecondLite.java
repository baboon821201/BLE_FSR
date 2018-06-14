package com.example.android.bluetoothlegatt;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
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
import android.support.v4.widget.NestedScrollView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.ProgressBar;
import android.widget.SimpleExpandableListAdapter;
import android.widget.Spinner;
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
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class ThirtySecondLite extends Activity {
    private final static String TAG = ThirtySecondLite.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    boolean stop = true;
    boolean start = false;
    //boolean catch_time = true;
    NestedScrollView nestedScrollView1;
    String time, filename1, filename2, l, ts1, time1, time2, diff;
    String[] dataArray;
    StringBuilder s1 = new StringBuilder();
    StringBuilder s2 = new StringBuilder();
    String genderSelect, ageInput, heightInput, weightInput, basicInformation;
    File path1, path2, file1, file2;
    Long tsLong1, tsLong2, timeP;
    long timeDelay;
    Date startTime ,endTime;
    float topRight, bottomRight, bottomLeft, topLeft, iAvg;
    int count = 0;
    float timeDelay_in_ms = 0;
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

    private LineChart mChart1, mChart2, mChart3, mChart4, mChart5;
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
        setContentView(R.layout.thirty_second_lite);
        //Environment.getDataDirectory();
        Log.d("TAG", "file path = " + Environment.getDownloadCacheDirectory());
        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        showDialog();
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
        /*
        initChart1();
        initChart2();
        initChart3();
        initChart4();
        */
        initChart5();

        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    private void showDialog()
    {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();

        alert.setTitle("Please Input Your Basic Information");
        alert.setCancelable(false);
        // 使用你設計的layout
        final View inputView = inflater.inflate(R.layout.input_basic_information, null);
        alert.setView(inputView);

        final Spinner gender = (Spinner)inputView.findViewById(R.id.gender_select);
        final String[] genders = {"Man", "Woman"};
        final ArrayAdapter<String> genderList = new ArrayAdapter<>(ThirtySecondLite.this,
                android.R.layout.simple_spinner_dropdown_item,
                genders);
        gender.setAdapter(genderList);
        gender.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                genderSelect = genders[position];
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        final EditText input1 = (EditText)inputView.findViewById(R.id.age);
        final EditText input2 = (EditText)inputView.findViewById(R.id.height);
        final EditText input3 = (EditText)inputView.findViewById(R.id.weight);

        alert.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                // 在此處理 input1 and input2

                ageInput = input1.getText().toString();
                heightInput = input2.getText().toString();
                weightInput = input3.getText().toString();
                basicInformation ="Age" + "_" + ageInput + "-"
                        + "Height" + "_" + heightInput + "-" + "Weight" + "_" + weightInput;
            }
        });
        alert.show();
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
        nestedScrollView1 = (NestedScrollView) findViewById(R.id.sv1);


        btnScan=(Button)findViewById(R.id.scan1);
        btnScan.setOnClickListener(StartScanClickListener);
        btnSave=(Button)findViewById(R.id.save1);
        btnSave.setEnabled(false);
        btnSave.setOnClickListener(SaveDataClickListener);
        btnClear = (Button)findViewById(R.id.clear1);
        btnClear.setEnabled(false);
        btnClear.setOnClickListener(ClearClickListener);

        //uploadInfoText = (TextView)findViewById(R.id.uploadInfoText);
    }

/*
    private void initChart1(){
        mChart1 = (LineChart)findViewById(R.id.chart1);
        // enable description text
        mChart1.getDescription().setEnabled(true);
        mChart1.getDescription().setText("Real Time Pressure");
        // enable touch gestures
        mChart1.setTouchEnabled(true);
        // enable scaling and dragging
        mChart1.setDragEnabled(true);
        mChart1.setScaleEnabled(true);
        mChart1.setDrawGridBackground(false);
        // if disabled, scaling can be done on x- and y-axis separately
        mChart1.setPinchZoom(true);
        // set an alternative background color
        mChart1.setBackgroundColor(Color.LTGRAY);
        LineData data = new LineData();
        data.setValueTextColor(Color.WHITE);
        // add empty data
        mChart1.setData(data);
        // get the legend (only possible after setting data)
        Legend l = mChart1.getLegend();
        // modify the legend ...
        l.setForm(Legend.LegendForm.LINE);
        l.setTypeface(mTfLight);
        l.setTextColor(Color.WHITE);
        XAxis xl = mChart1.getXAxis();
        xl.setTypeface(mTfLight);
        xl.setTextColor(Color.WHITE);
        xl.setDrawGridLines(false);
        xl.setAvoidFirstLastClipping(true);
        xl.setEnabled(true);
        YAxis leftAxis = mChart1.getAxisLeft();
        leftAxis.setTypeface(mTfLight);
        leftAxis.setTextColor(Color.WHITE);
        leftAxis.setAxisMaximum(1000f);
        leftAxis.setAxisMinimum(0f);
        leftAxis.setDrawGridLines(true);
        YAxis rightAxis = mChart1.getAxisRight();
        rightAxis.setEnabled(false);
    }

    private void initChart2(){
        mChart2 = (LineChart)findViewById(R.id.chart2);
        // enable description text
        mChart2.getDescription().setEnabled(true);
        mChart2.getDescription().setText("Real Time Pressure");
        // enable touch gestures
        mChart2.setTouchEnabled(true);
        // enable scaling and dragging
        mChart2.setDragEnabled(true);
        mChart2.setScaleEnabled(true);
        mChart2.setDrawGridBackground(false);
        // if disabled, scaling can be done on x- and y-axis separately
        mChart2.setPinchZoom(true);
        // set an alternative background color
        mChart2.setBackgroundColor(Color.LTGRAY);
        LineData data = new LineData();
        data.setValueTextColor(Color.WHITE);
        // add empty data
        mChart2.setData(data);
        // get the legend (only possible after setting data)
        Legend l = mChart2.getLegend();
        // modify the legend ...
        l.setForm(Legend.LegendForm.LINE);
        l.setTypeface(mTfLight);
        l.setTextColor(Color.WHITE);
        XAxis xl = mChart2.getXAxis();
        xl.setTypeface(mTfLight);
        xl.setTextColor(Color.WHITE);
        xl.setDrawGridLines(false);
        xl.setAvoidFirstLastClipping(true);
        xl.setEnabled(true);
        YAxis leftAxis = mChart2.getAxisLeft();
        leftAxis.setTypeface(mTfLight);
        leftAxis.setTextColor(Color.WHITE);
        leftAxis.setAxisMaximum(1000f);
        leftAxis.setAxisMinimum(0f);
        leftAxis.setDrawGridLines(true);
        YAxis rightAxis = mChart2.getAxisRight();
        rightAxis.setEnabled(false);
    }

    private void initChart3(){
        mChart3 = (LineChart)findViewById(R.id.chart3);
        // enable description text
        mChart3.getDescription().setEnabled(true);
        mChart3.getDescription().setText("Real Time Pressure");
        // enable touch gestures
        mChart3.setTouchEnabled(true);
        // enable scaling and dragging
        mChart3.setDragEnabled(true);
        mChart3.setScaleEnabled(true);
        mChart3.setDrawGridBackground(false);
        // if disabled, scaling can be done on x- and y-axis separately
        mChart3.setPinchZoom(true);
        // set an alternative background color
        mChart3.setBackgroundColor(Color.LTGRAY);
        LineData data = new LineData();
        data.setValueTextColor(Color.WHITE);
        // add empty data
        mChart3.setData(data);
        // get the legend (only possible after setting data)
        Legend l = mChart3.getLegend();
        // modify the legend ...
        l.setForm(Legend.LegendForm.LINE);
        l.setTypeface(mTfLight);
        l.setTextColor(Color.WHITE);
        XAxis xl = mChart3.getXAxis();
        xl.setTypeface(mTfLight);
        xl.setTextColor(Color.WHITE);
        xl.setDrawGridLines(false);
        xl.setAvoidFirstLastClipping(true);
        xl.setEnabled(true);
        YAxis leftAxis = mChart3.getAxisLeft();
        leftAxis.setTypeface(mTfLight);
        leftAxis.setTextColor(Color.WHITE);
        leftAxis.setAxisMaximum(1000f);
        leftAxis.setAxisMinimum(0f);
        leftAxis.setDrawGridLines(true);
        YAxis rightAxis = mChart3.getAxisRight();
        rightAxis.setEnabled(false);
    }

    private void initChart4(){
        mChart4 = (LineChart)findViewById(R.id.chart4);
        // enable description text
        mChart4.getDescription().setEnabled(true);
        mChart4.getDescription().setText("Real Time Pressure");
        // enable touch gestures
        mChart4.setTouchEnabled(true);
        // enable scaling and dragging
        mChart4.setDragEnabled(true);
        mChart4.setScaleEnabled(true);
        mChart4.setDrawGridBackground(false);
        // if disabled, scaling can be done on x- and y-axis separately
        mChart4.setPinchZoom(true);
        // set an alternative background color
        mChart4.setBackgroundColor(Color.LTGRAY);
        LineData data = new LineData();
        data.setValueTextColor(Color.WHITE);
        // add empty data
        mChart4.setData(data);
        // get the legend (only possible after setting data)
        Legend l = mChart4.getLegend();
        // modify the legend ...
        l.setForm(Legend.LegendForm.LINE);
        l.setTypeface(mTfLight);
        l.setTextColor(Color.WHITE);
        XAxis xl = mChart4.getXAxis();
        xl.setTypeface(mTfLight);
        xl.setTextColor(Color.WHITE);
        xl.setDrawGridLines(false);
        xl.setAvoidFirstLastClipping(true);
        xl.setEnabled(true);
        YAxis leftAxis = mChart4.getAxisLeft();
        leftAxis.setTypeface(mTfLight);
        leftAxis.setTextColor(Color.WHITE);
        leftAxis.setAxisMaximum(1000f);
        leftAxis.setAxisMinimum(0f);
        leftAxis.setDrawGridLines(true);
        YAxis rightAxis = mChart4.getAxisRight();
        rightAxis.setEnabled(false);
    }

    */
    private void initChart5(){
        mChart5 = (LineChart)findViewById(R.id.chart5);
        // enable description text
        mChart5.getDescription().setEnabled(true);
        mChart5.getDescription().setText("Real Time Pressure");
        // enable touch gestures
        mChart5.setTouchEnabled(true);
        // enable scaling and dragging
        mChart5.setDragEnabled(true);
        mChart5.setScaleEnabled(true);
        mChart5.setDrawGridBackground(false);
        // if disabled, scaling can be done on x- and y-axis separately
        mChart5.setPinchZoom(true);
        // set an alternative background color
        mChart5.setBackgroundColor(Color.LTGRAY);
        LineData data = new LineData();
        data.setValueTextColor(Color.WHITE);
        // add empty data
        mChart5.setData(data);
        // get the legend (only possible after setting data)
        Legend l = mChart5.getLegend();
        // modify the legend ...
        l.setForm(Legend.LegendForm.LINE);
        l.setTypeface(mTfLight);
        l.setTextColor(Color.WHITE);
        XAxis xl = mChart5.getXAxis();
        xl.setTypeface(mTfLight);
        xl.setTextColor(Color.WHITE);
        xl.setDrawGridLines(false);
        xl.setAvoidFirstLastClipping(true);
        xl.setEnabled(true);
        YAxis leftAxis = mChart5.getAxisLeft();
        leftAxis.setTypeface(mTfLight);
        leftAxis.setTextColor(Color.WHITE);
        leftAxis.setAxisMaximum(1000f);
        leftAxis.setAxisMinimum(0f);
        leftAxis.setDrawGridLines(true);
        YAxis rightAxis = mChart5.getAxisRight();
        rightAxis.setEnabled(false);
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
        /*
        LineData data1 = mChart1.getData();
        LineData data2 = mChart2.getData();
        LineData data3 = mChart3.getData();
        LineData data4 = mChart4.getData();
        */
        LineData data5 = mChart5.getData();
/*
        if (data1 != null) {
            ILineDataSet L1 = data1.getDataSetByIndex(0);
            // set.addEntry(...); // can be called as well
            if (L1 == null) {
                L1 = set_L1();
                data1.addDataSet(L1);
            }
            data1.addEntry(new Entry(L1.getEntryCount(), topRight), 0);
            data1.notifyDataChanged();

            // let the chart know it's data has changed
            mChart1.notifyDataSetChanged();

            // limit the number of visible entries
            mChart1.setVisibleXRangeMaximum(120);
            // mChart.setVisibleYRange(30, AxisDependency.LEFT);

            // move to the latest entry
            mChart1.moveViewToX(data1.getEntryCount());
            // this automatically refreshes the chart (calls invalidate())
            // mChart.moveViewTo(data.getXValCount()-7, 55f,
            // AxisDependency.LEFT);
        }

        if (data2 != null) {
            ILineDataSet L2 = data2.getDataSetByIndex(0);
            // set.addEntry(...); // can be called as well
            if (L2 == null) {
                L2 = set_L2();
                data2.addDataSet(L2);
            }
            data2.addEntry(new Entry(L2.getEntryCount(), bottomRight), 0);
            data2.notifyDataChanged();

            // let the chart know it's data has changed
            mChart2.notifyDataSetChanged();

            // limit the number of visible entries
            mChart2.setVisibleXRangeMaximum(120);
            // mChart.setVisibleYRange(30, AxisDependency.LEFT);

            // move to the latest entry
            mChart2.moveViewToX(data2.getEntryCount());
            // this automatically refreshes the chart (calls invalidate())
            // mChart.moveViewTo(data.getXValCount()-7, 55f,
            // AxisDependency.LEFT);
        }

        if (data3 != null) {
            ILineDataSet L3 = data3.getDataSetByIndex(0);
            // set.addEntry(...); // can be called as well
            if (L3 == null) {
                L3 = set_L3();
                data3.addDataSet(L3);
            }
            data3.addEntry(new Entry(L3.getEntryCount(), bottomLeft), 0);
            data3.notifyDataChanged();

            // let the chart know it's data has changed
            mChart3.notifyDataSetChanged();

            // limit the number of visible entries
            mChart3.setVisibleXRangeMaximum(120);
            // mChart.setVisibleYRange(30, AxisDependency.LEFT);

            // move to the latest entry
            mChart3.moveViewToX(data3.getEntryCount());
            // this automatically refreshes the chart (calls invalidate())
            // mChart.moveViewTo(data.getXValCount()-7, 55f,
            // AxisDependency.LEFT);
        }

        if (data4 != null) {
            ILineDataSet L4 = data4.getDataSetByIndex(0);
            // set.addEntry(...); // can be called as well
            if (L4 == null) {
                L4 = set_L4();
                data4.addDataSet(L4);
            }
            data4.addEntry(new Entry(L4.getEntryCount(), topLeft), 0);
            data4.notifyDataChanged();

            // let the chart know it's data has changed
            mChart4.notifyDataSetChanged();

            // limit the number of visible entries
            mChart4.setVisibleXRangeMaximum(120);
            // mChart.setVisibleYRange(30, AxisDependency.LEFT);

            // move to the latest entry
            mChart4.moveViewToX(data4.getEntryCount());
            // this automatically refreshes the chart (calls invalidate())
            // mChart.moveViewTo(data.getXValCount()-7, 55f,
            // AxisDependency.LEFT);
        }
*/
        if (data5 != null) {
            ILineDataSet L5 = data5.getDataSetByIndex(0);
            // set.addEntry(...); // can be called as well
            if (L5 == null) {
                L5 = set_L5();
                data5.addDataSet(L5);
            }
            data5.addEntry(new Entry(L5.getEntryCount(), iAvg), 0);
            data5.notifyDataChanged();

            // let the chart know it's data has changed
            mChart5.notifyDataSetChanged();

            // limit the number of visible entries
            mChart5.setVisibleXRangeMaximum(120);
            // mChart.setVisibleYRange(30, AxisDependency.LEFT);

            // move to the latest entry
            mChart5.moveViewToX(data5.getEntryCount());
            // this automatically refreshes the chart (calls invalidate())
            // mChart.moveViewTo(data.getXValCount()-7, 55f,
            // AxisDependency.LEFT);
        }

    }

    /*
    private LineDataSet set_L1() {
        LineDataSet set = new LineDataSet(null, "Right Front");
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setColor(Color.RED);
        set.setCircleColor(Color.WHITE);
        set.setLineWidth(2f);
        set.setCircleRadius(2f);
        set.setFillAlpha(65);
        set.setFillColor(ColorTemplate.getHoloBlue());
        set.setHighLightColor(Color.rgb(255, 0, 0));
        set.setValueTextColor(Color.WHITE);
        set.setValueTextSize(6f);
        set.setDrawValues(false);
        return set;
    }

    private LineDataSet set_L2() {
        LineDataSet set = new LineDataSet(null, "Right Rear");
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setColor(Color.BLUE);
        set.setCircleColor(Color.WHITE);
        set.setLineWidth(2f);
        set.setCircleRadius(2f);
        set.setFillAlpha(65);
        set.setFillColor(ColorTemplate.getHoloBlue());
        set.setHighLightColor(Color.rgb(255, 0, 0));
        set.setValueTextColor(Color.WHITE);
        set.setValueTextSize(6f);
        set.setDrawValues(false);
        return set;
    }

    private LineDataSet set_L3() {
        LineDataSet set = new LineDataSet(null, "Left Rear");
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setColor(Color.YELLOW);
        set.setCircleColor(Color.WHITE);
        set.setLineWidth(2f);
        set.setCircleRadius(2f);
        set.setFillAlpha(65);
        set.setFillColor(ColorTemplate.getHoloBlue());
        set.setHighLightColor(Color.rgb(255, 0, 0));
        set.setValueTextColor(Color.WHITE);
        set.setValueTextSize(6f);
        set.setDrawValues(false);
        return set;
    }

    private LineDataSet set_L4() {
        LineDataSet set = new LineDataSet(null, "Left Front");
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setColor(Color.GREEN);
        set.setCircleColor(Color.WHITE);
        set.setLineWidth(2f);
        set.setCircleRadius(2f);
        set.setFillAlpha(65);
        set.setFillColor(ColorTemplate.getHoloBlue());
        set.setHighLightColor(Color.rgb(255, 0, 0));
        set.setValueTextColor(Color.WHITE);
        set.setValueTextSize(6f);
        set.setDrawValues(false);
        return set;
    }

    */
    private LineDataSet set_L5() {
        LineDataSet set = new LineDataSet(null, "Average");
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setColor(Color.MAGENTA);
        set.setCircleColor(Color.WHITE);
        set.setLineWidth(2f);
        set.setCircleRadius(2f);
        set.setFillAlpha(65);
        set.setFillColor(ColorTemplate.getHoloBlue());
        set.setHighLightColor(Color.rgb(255, 0, 0));
        set.setValueTextColor(Color.WHITE);
        set.setValueTextSize(6f);
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
                s1.append(data);
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
            topRight = Float.valueOf(dataArray[1]);   //右上
            bottomRight = Float.valueOf(dataArray[2]);   //右下
            bottomLeft = Float.valueOf(dataArray[3]);   //左下
            topLeft = Float.valueOf(dataArray[4]);   //左上
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



            //j=0,a=1

            if(bottomRight>10.0 && topRight>10.0 && bottomLeft>10.0 && topLeft>10.0
                    && bottomRight>topRight && bottomLeft>topLeft && iAvg>=250.0 && !start){
                //if (catch_time) {
                time1 = dataArray[0];

                DateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
                try {
                    startTime = sdf.parse(time1);
                } catch (ParseException e) {
                    e.printStackTrace();
                }

                //startTime = new Date(System.currentTimeMillis());
                //SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
                String dateString = sdf.format(startTime);
                Log.d(TAG, dateString);
                start = true;
                //catch_time = false;
/*
                    tsLong1 = System.currentTimeMillis();
                    t1 = tsLong1.intValue();
                    String ts = tsLong1.toString();
                    //Log.d(TAG, ts);
*/
                //}
            }else{
                if((iAvg<250.0) && (start)){
                    //plus_1++;

                    time2 = dataArray[0];

                    DateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
                    try {
                        endTime = sdf.parse(time2);
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }

                    //endTime = new Date(System.currentTimeMillis());
                    //SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
                    String dateString = sdf.format(endTime);
                    Log.d(TAG, dateString);

                    timeDelay = endTime.getTime() - startTime.getTime();
                    timeDelay_in_ms = (float)timeDelay / 1000;
                    diff = String.valueOf(timeDelay_in_ms);
                    Log.d(TAG, diff);

                    count++;
                    l = Integer.toString(count);
                    Log.d(TAG, l);
                    mCounter.append(l + "\n");
                    mDelay.append(diff + "\n");

                    nestedScrollView1.post(new Runnable() {
                        @Override
                        public void run() {
                            nestedScrollView1.fullScroll(View.FOCUS_DOWN);
                        }
                    });

                    String times_timeDelay = l + "," +diff + "\n";

                    s2.append(times_timeDelay);
                    start = false;
                    //plus_1 = 0;
                    //catch_time = true;
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
                }
            }





            //String j1 = Integer.toString(start);
            //Log.d(TAG, j1);

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
            mTimer.setText(String.valueOf(millisUntilFinished / 1000) + "." + String.valueOf(millisUntilFinished % 1000));
        }
        @Override
        public void onFinish() {
            mTimer.setText("0.000");
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
                SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
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
                mTimer.setText("30.000");
                mCounter.setText("");
                mDelay.setText("");
                //s.setLength(0);
                start = false;
                //catch_time = true;
                //plus_1 = 0;
                count = 0;
                btnScan.setEnabled(true);
                btnSave.setEnabled(false);
                btnClear.setEnabled(false);
/*
                mChart1.clearValues();
                mChart2.clearValues();
                mChart3.clearValues();
                mChart4.clearValues();
                */
                mChart5.clearValues();
                s1.setLength(0);
                s2.setLength(0);
                //uploadInfoText.setText("");
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
                    filename1 = basicInformation + "-all_data" + ".csv";
                    Log.d(TAG, "filename = " + filename1);

                    path1 = Environment.getExternalStoragePublicDirectory("/FSR/Thirty Second Mode/" + genderSelect + "/" + time + "/");
                    file1 = new File(path1, filename1);
                    Log.d(TAG, "path = " + path1);

                    try {
                        path1.mkdirs();
                        OutputStream outputStream = new FileOutputStream(file1, true);
                        //String s1 = "Time" + ",\t\t" + "sensor1" + ",\t" + "sensor2" + ",\t" + "sensor3" + ",\t" + "sensor4" + ",\t" + "Average" + ",\n";
                        String title1 = "Time" + "," + "Top Right" + "," + "Bottom Right"+ "," + "Bottom Left"+ "," + "Top Left"+ "," + "Average" + "\n";
                        s1.toString();
                        //String dataArrayString = saveData;
                        String all1 = title1 + s1;

                        outputStream.write(all1.getBytes());
                        outputStream.close();
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        Log.w("ExternalStorage", "Error writing " + file1, e);
                    }
                    //Toast.makeText(DeviceControlActivity.this, "Save in:" + path  + "/"+ filename, Toast.LENGTH_LONG).show();

                    filename2 = basicInformation + "-test_data" + ".csv";
                    Log.d(TAG, "filename = " + filename2);

                    //path2 = Environment.getExternalStoragePublicDirectory("/FSR/Thirty Second Mode/" + genderSelect + "/" + time + "/");
                    file2 = new File(path1, filename2);
                    Log.d(TAG, "path = " + path1);

                    try {
                        path1.mkdirs();
                        OutputStream outputStream = new FileOutputStream(file2, true);

                        String title2 = "Times" + "," + "Time Delay(s)" + "\n";
                        s2.toString();
                        String all2 = title2 + s2;

                        outputStream.write(all2.getBytes());
                        outputStream.close();

                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        Log.w("ExternalStorage", "Error writing " + file2, e);
                    }
                    //Toast.makeText(DeviceControlActivity.this, "Save in:" + path  + "/"+ filename, Toast.LENGTH_LONG).show();



                }
                else{
                    Toast.makeText(ThirtySecondLite.this, "no storage", Toast.LENGTH_LONG).show();
                }

                /*
                String s1 = "Time" + "\t" + "sensor1" + "\t" + "sensor2" + "\t" + "sensor3" + "\t" + "sensor4" + "\t" + "Average" + "\n";
                String s2 = mTime + "\t" + mS1 + "\t" + mS2 + "\t" + mS3 + "\t" + mS4 + "\t" + mAvg;
                String all = s1 + s2;
                            */
                String name1 = filename1.toString();
                String filePath1 = Environment.getExternalStorageDirectory().toString() + "/FSR/Thirty Second Mode/" + genderSelect + "/" + time + "/" + name1;

                uploadData1(filePath1);

                String name2 = filename2.toString();
                String filePath2 = Environment.getExternalStorageDirectory().toString() + "/FSR/Thirty Second Mode/" + genderSelect + "/" + time + "/" + name2;

                uploadData2(filePath2);

                Toast.makeText(ThirtySecondLite.this, "All data save in:" + filePath1, Toast.LENGTH_SHORT).show();
                Toast.makeText(ThirtySecondLite.this, "Test data save in:" + filePath2, Toast.LENGTH_SHORT).show();

                //DataUploadProgress.setVisibility(View.VISIBLE);
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

    private void uploadData1(String path){
        Uri file = Uri.fromFile(new File(path));
        StorageMetadata metadata = new StorageMetadata.Builder()
                .setContentType("data/csv")
                .build();
        dataRef = mStorageRef.child("/Thirty Second Mode/" + genderSelect + "/" + time + "/" + filename1);
        UploadTask uploadTask = dataRef.putFile(file, metadata);
        uploadTask.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception exception) {
                //uploadInfoText.setText(exception.getMessage());
                Toast.makeText(ThirtySecondLite.this, exception.getMessage(), Toast.LENGTH_LONG).show();
            }
        }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                //uploadInfoText.setText(R.string.up_load_success);
                Toast.makeText(ThirtySecondLite.this, "All Data Up Load Success", Toast.LENGTH_SHORT).show();
            }
        }).addOnProgressListener(new OnProgressListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onProgress(UploadTask.TaskSnapshot taskSnapshot) {
                /*
                int progress = (int)((100.0 * taskSnapshot.getBytesTransferred()) / taskSnapshot.getTotalByteCount());
                DataUploadProgress.setProgress(progress);
                if(progress >= 100){
                    DataUploadProgress.setVisibility(View.GONE);
                }
                */
            }
        });
    }

    private void uploadData2(String path){
        Uri file = Uri.fromFile(new File(path));
        StorageMetadata metadata = new StorageMetadata.Builder()
                .setContentType("data/csv")
                .build();
        dataRef = mStorageRef.child("/Thirty Second Mode/" + genderSelect + "/" + time + "/" + filename2);
        UploadTask uploadTask = dataRef.putFile(file, metadata);
        uploadTask.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception exception) {
                //uploadInfoText.setText(exception.getMessage());
                Toast.makeText(ThirtySecondLite.this, exception.getMessage(), Toast.LENGTH_LONG).show();
            }
        }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                //uploadInfoText.setText(R.string.up_load_success);
                Toast.makeText(ThirtySecondLite.this, "Test Data Up Load Success", Toast.LENGTH_SHORT).show();
            }
        }).addOnProgressListener(new OnProgressListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onProgress(UploadTask.TaskSnapshot taskSnapshot) {
                /*
                int progress = (int)((100.0 * taskSnapshot.getBytesTransferred()) / taskSnapshot.getTotalByteCount());
                DataUploadProgress.setProgress(progress);
                if(progress >= 100){
                    DataUploadProgress.setVisibility(View.GONE);
                }
                */
            }
        });
    }
}