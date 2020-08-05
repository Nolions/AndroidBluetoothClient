package tw.nolions.BluetoothConnect_Client;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private final String TAG = "BluetoothConnect_Client";
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private ArrayList<Map<String, String>> mDeviceList;
    private SimpleAdapter mDeviceAdapter;

    private ConnectThread ct;

    private BluetoothAdapter mBluetoothAdapter;

    private Handler handler = new MainHandler(this);
    private EditText mMsgEditText;
    private ArrayList<BluetoothDevice> mPairedDevices;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            //收到bluetooth狀態改變
            if (intent.getAction() != null) {
                Log.d(TAG, "MainActivity::mReceiver::onReceive(), action:" + intent.getAction());
                // TODO 接收到不同狀態後要做的事情
                switch (intent.getAction()) {
                    case BluetoothDevice.ACTION_ACL_CONNECTED:
                        break;
                    case BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED:
                        break;
                    case BluetoothDevice.ACTION_ACL_DISCONNECTED:
                        break;
                    case BluetoothAdapter.ACTION_STATE_CHANGED:
                        break;
                }
            }
        }
    };

    public MainActivity() {
        mDeviceList = new ArrayList<>();
        mPairedDevices = new ArrayList<>();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mMsgEditText = findViewById(R.id.msgEditText);
        ListView mDeviceListView = findViewById(R.id.pairedDevicesListView);

        mDeviceAdapter = new SimpleAdapter(
                this,
                mDeviceList,
                R.layout.device_item,
                new String[]{"name", "address"},
                new int[]{R.id.deviceName, R.id.deviceAddress}
        );

        mDeviceListView.setAdapter(mDeviceAdapter);

        mDeviceListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, final int position, long id) {
                Log.d(TAG, "device's ListView click name of:" + mPairedDevices.get(position).getName());
                Log.d(TAG, "device's ListView click address of:" + mPairedDevices.get(position).getAddress());

                // 向指定裝置進行連線
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        // 建立連線前先關閉之前連線
                        if (ct != null) {
                            ct.cancel();
                        }
                        ct = new ConnectThread(mPairedDevices.get(position));
                        ct.run();
                    }
                }).start();
            }
        });

        init();
    }

    private void init() {
        registerBroadcastReceiver();
        initBluetooth();
    }

    /**
     * 藍芽相關設置
     * =================
     * 1. 是否支援藍芽
     * 2. 是否啟動藍芽
     * 3. 取得以配對裝置
     */
    private void initBluetooth() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // 檢查裝置是否支援藍牙
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "該裝置不支援藍牙", Toast.LENGTH_LONG).show();
        }

        // 檢查藍芽是否啟動
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);

            int REQUEST_ENABLE_BT = 1;
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        // 取得目前裝置已完成配對裝置
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();

        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                Map<String, String> deviceMap = new HashMap<>();
                deviceMap.put("name", device.getName());
                deviceMap.put("address", device.getAddress());

                mPairedDevices.add(device);
                mDeviceList.add(deviceMap);
            }

            // 刷新 已經配對藍牙裝置列表
            mDeviceAdapter.notifyDataSetChanged();
        }
    }

    /**
     * 註冊BroadcastReceiver事件
     * ============================
     * 1. 連線狀態改變
     * 2. 連線建立
     * 3. 中斷連線請求
     * 4. 中斷連線
     */
    private void registerBroadcastReceiver() {
        IntentFilter intent = new IntentFilter();
        intent.addAction(BluetoothDevice.ACTION_ACL_CONNECTED); // 連線建立
        intent.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED); // 中斷連線請求，並中斷連線
        intent.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED); // 中斷連線
        intent.addAction(BluetoothAdapter.ACTION_STATE_CHANGED); // 連線狀態改變
        registerReceiver(mReceiver, intent);
    }

    /**
     * Send Button onClick Listener
     *
     * @param view View
     */
    public void send(View view) {
        String msg = mMsgEditText.getText().toString();

        if (msg.equals("")) {
            // 沒有輸入訊息
            Log.e(TAG, "message is empty");
            return;
        }

        if (ct == null) {
            // 尚未建立藍牙連線
            Log.e(TAG, "no connection");
            return;
        }

        // 清空輸入訊息輸入欄
        mMsgEditText.setText("");
        ct.write(msg);
    }

    /**
     * 處理藍芽連線Thread
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket mBluetoothSocket;
        private OutputStream mOutputStream;
        private InputStream mInputStream;

        private ConnectThread(BluetoothDevice device) {
            Log.d(TAG, "ConnectThread");
            // Use a temporary object that is later assigned to mmSocket,
            // because mmSocket is final
            BluetoothSocket tmp = null;

            // Get a BluetoothSocket to connect with the given BluetoothDevice
            try {
                // MY_UUID is the app's UUID string, also used by the server code
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
            }
            mBluetoothSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it will slow down the connection
            mBluetoothAdapter.cancelDiscovery();

            try {
                // Connect the device through the socket. This will block
                // until it succeeds or throws an exception
                // 建立藍芽連線
                mBluetoothSocket.connect();

                //
                mOutputStream = mBluetoothSocket.getOutputStream();

                //
                mInputStream = mBluetoothSocket.getInputStream();

                // 連線建建立，馬上發送一則訊息(非必要)
                if (mOutputStream != null) {
                    // 需要发送的信息
                    String text = "連線建立";

                    mOutputStream.write(text.getBytes(StandardCharsets.UTF_8));
                    Log.d(TAG, "msg:" + text);

                    Message msg = new Message();
                    msg.obj = text;
                    handler.sendMessage(msg);
                }

                read();
            } catch (IOException connectException) {
                Log.e(TAG, "ConnectThread::run(), connectException:" + connectException.getMessage());
                // 如果無法於裝置建立連線，則一同關閉連線通道
                try {
                    mBluetoothSocket.close();
                } catch (IOException closeException) {
                    Log.e(TAG, "ConnectThread::run(), closeException:" + closeException.getMessage());
                }
            }
        }

        /**
         * 寫入訊息到藍芽連線中
         *
         * @param data String
         */
        private void write(String data) {
            try {
                if (mOutputStream != null) {
                    Log.d(TAG, "ConnectThread::write(), message:" + data);
                    // 以位元把使用utf-8的格式進行藍芽傳送
                    mOutputStream.write(data.getBytes(StandardCharsets.UTF_8));

                    // 成功發送訊息後，顯示發送成功訊息。(可省略)
                    Message msg = new Message();
                    msg.obj = "Send Message success";
                    handler.sendMessage(msg);
                }
            } catch (IOException e) {
                Log.e(TAG, "ConnectThread::write(), error:" + e.getMessage());
            }
        }

        /**
         * 從藍芽連線中取得訊息
         */
        private void read() {
            while (true) {
                try {
                    byte[] buffer = new byte[128];
                    Message msg = new Message();
                    msg.obj = new String(buffer, 0, mInputStream.read(buffer), StandardCharsets.UTF_8);
                    Log.d(TAG, "ConnectThread::read(), msg:" + msg.obj);
                    handler.sendMessage(msg);
                } catch (IOException e) {
                    Log.e(TAG, "ConnectThread::read(), error:" + e.getMessage());
                    break;
                }
            }
        }

        /**
         * 關閉藍芽連線
         */
        private void cancel() {
            try {
                mBluetoothSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "ConnectThread::cancel(), exception: " + e.getMessage());
            }
        }
    }

    private static class MainHandler extends Handler {

        private Activity mActivity;

        private MainHandler(Activity activity) {
            mActivity = activity;
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            Toast.makeText(mActivity, "訊息:" + msg.obj, Toast.LENGTH_SHORT).show();
        }
    }
}

