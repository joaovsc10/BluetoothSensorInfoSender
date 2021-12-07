package mycareshoe.bluetoothsensorsender;

import android.app.Activity;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import mycareshoe.bluetoothsensorsender.R;
import mycareshoe.bluetoothsensorsender.ChatController;

public class MainActivity extends AppCompatActivity {

    private TextView status;
    private Button btnConnect;
    private ListView listView;
    private Dialog dialog;
    private ArrayAdapter<String> chatAdapter;
    private ArrayList<String> chatMessages;
    private BluetoothAdapter bluetoothAdapter;

    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_OBJECT = 4;
    public static final int MESSAGE_TOAST = 5;
    public static final String DEVICE_OBJECT = "device_name";

    private static final int REQUEST_ENABLE_BLUETOOTH = 1;
    private ChatController chatController;
    private BluetoothDevice connectingDevice;
    private ArrayAdapter<String> discoveredDevicesAdapter;
    private Spinner sendingOptionsSpinner;
    ArrayAdapter sendingOptionsAdapter;
    private String sendingOption;


    List<String> sendingOptions = Arrays.asList("", "Random values", "Mid-swing", "Random values without warning", "Heel strike", "Walk simulation - Left foot", "Walk simulation - Right foot");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        findViewsByIds();

        sendingOptionsSpinner = setSpinner(sendingOptionsSpinner, R.id.spinnerSendModes, sendingOptions, sendingOptionsAdapter, this.getWindow().getDecorView().findViewById(android.R.id.content));

        sendingOptionsSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long arg3) {
                sendingOption = sendingOptions.get(position).toString();

            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }

        });


        //check device support bluetooth or not
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available!", Toast.LENGTH_SHORT).show();
            finish();
        }

        //show bluetooth devices dialog when click connect button
        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showPrinterPickDialog();
            }
        });

        //set chat adapter
        chatMessages = new ArrayList<>();
        chatAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, chatMessages);
        listView.setAdapter(chatAdapter);
    }

    private Handler handler = new Handler(new Handler.Callback() {

        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case ChatController.STATE_CONNECTED:
                            setStatus("Connected to: " + connectingDevice.getName());
                            btnConnect.setEnabled(false);
                            break;
                        case ChatController.STATE_CONNECTING:
                            setStatus("Connecting...");
                            btnConnect.setEnabled(false);
                            break;
                        case ChatController.STATE_LISTEN:
                        case ChatController.STATE_NONE:
                            setStatus("Not connected");
                            break;
                    }
                    break;
                case MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;

                    String writeMessage = new String(writeBuf);
                    chatMessages.add("Me: " + writeMessage);
                    chatAdapter.notifyDataSetChanged();
                    break;
                case MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;

                    String readMessage = new String(readBuf, 0, msg.arg1);
                    chatMessages.add(connectingDevice.getName() + ":  " + readMessage);
                    chatAdapter.notifyDataSetChanged();
                    break;
                case MESSAGE_DEVICE_OBJECT:
                    connectingDevice = msg.getData().getParcelable(DEVICE_OBJECT);
                    Toast.makeText(getApplicationContext(), "Connected to " + connectingDevice.getName(),
                            Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_TOAST:
                    Toast.makeText(getApplicationContext(), msg.getData().getString("toast"),
                            Toast.LENGTH_SHORT).show();
                    break;
            }
            return false;
        }
    });

    private void showPrinterPickDialog() {
        dialog = new Dialog(this);
        dialog.setContentView(R.layout.layout_bluetooth);
        dialog.setTitle("Bluetooth Devices");

        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
        bluetoothAdapter.startDiscovery();

        //Initializing bluetooth adapters
        ArrayAdapter<String> pairedDevicesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        discoveredDevicesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);

        //locate listviews and attatch the adapters
        ListView listView = (ListView) dialog.findViewById(R.id.pairedDeviceList);
        ListView listView2 = (ListView) dialog.findViewById(R.id.discoveredDeviceList);
        listView.setAdapter(pairedDevicesAdapter);
        listView2.setAdapter(discoveredDevicesAdapter);

        // Register for broadcasts when a device is discovered
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(discoveryFinishReceiver, filter);

        // Register for broadcasts when discovery has finished
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(discoveryFinishReceiver, filter);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        // If there are paired devices, add each one to the ArrayAdapter
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                pairedDevicesAdapter.add(device.getName() + "\n" + device.getAddress());
            }
        } else {
            pairedDevicesAdapter.add(getString(R.string.none_paired));
        }

        //Handling listview item click event
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                bluetoothAdapter.cancelDiscovery();
                String info = ((TextView) view).getText().toString();
                String address = info.substring(info.length() - 17);

                connectToDevice(address);
                dialog.dismiss();
            }

        });

        listView2.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                bluetoothAdapter.cancelDiscovery();
                String info = ((TextView) view).getText().toString();
                String address = info.substring(info.length() - 17);

                connectToDevice(address);
                dialog.dismiss();
            }
        });

        dialog.findViewById(R.id.cancelButton).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        dialog.setCancelable(false);
        dialog.show();
    }

    private void setStatus(String s) {
        status.setText(s);
    }

    private void connectToDevice(String deviceAddress) {
        bluetoothAdapter.cancelDiscovery();
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
        chatController.connect(device);
    }

    private void findViewsByIds() {
        status = (TextView) findViewById(R.id.status);
        btnConnect = (Button) findViewById(R.id.btn_connect);
        listView = (ListView) findViewById(R.id.list);
        View btnSend = findViewById(R.id.btn_send);


        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Random rand = new Random();

                switch (sendingOption) {
                    case "Random values":

                        sendMessage(Integer.toString(rand.nextInt(300)) + "|" + Integer.toString(rand.nextInt(300)) + "|" + Integer.toString(rand.nextInt(300)) + "|" + Integer.toString(rand.nextInt(300)) + "|" + Integer.toString(rand.nextInt(300)) + "|" + Integer.toString(rand.nextInt(300)) + "|" + Integer.toString(rand.nextInt(300)) + "|" + Integer.toString(rand.nextInt(300)) + "|" + Integer.toString(rand.nextInt(300)) + "|" + Integer.toString(rand.nextInt(300)) + "|" + Integer.toString(rand.nextInt(300)) + "|" + Integer.toString(rand.nextInt(300)) + "|" + Integer.toString(rand.nextInt(300)) + "|" + Integer.toString(rand.nextInt(100)) + "|" + Integer.toString(rand.nextInt(50)));

                        break;
                    case "Mid-swing":
                        sendMessage("0|0|0|0|0|0|0|0|0|0|0|0|0|" + Integer.toString(rand.nextInt(100)) + "|" + Integer.toString(rand.nextInt(50)));
                        break;
                    case "Toe off":
                        sendMessage(Integer.toString(rand.nextInt(299) + 1) + "|0|0|0|0|0|0|0|0|0|0|0|0|" + Integer.toString(rand.nextInt(100)) + "|" + Integer.toString(rand.nextInt(50)));

                        break;
                    case "Heel strike":
                        sendMessage("0|0|0|0|0|0|" + Integer.toString(rand.nextInt(100) + 1) + "|" + Integer.toString(rand.nextInt(100) + 1) + "|" + Integer.toString(rand.nextInt(100) + 1) + "|0|0|0|0|" + Integer.toString(rand.nextInt(100)) + "|" + Integer.toString(rand.nextInt(50)));
                        break;
                    case "Random values without warning":

                        sendMessage(Integer.toString(rand.nextInt(199)) + "|" + Integer.toString(rand.nextInt(199)) + "|" + Integer.toString(rand.nextInt(199)) + "|" + Integer.toString(rand.nextInt(199)) + "|" + Integer.toString(rand.nextInt(199)) + "|" + Integer.toString(rand.nextInt(199)) + "|" + Integer.toString(rand.nextInt(199)) + "|" + Integer.toString(rand.nextInt(199)) + "|" + Integer.toString(rand.nextInt(199)) + "|" + Integer.toString(rand.nextInt(199)) + "|" + Integer.toString(rand.nextInt(199)) + "|" + Integer.toString(rand.nextInt(199)) + "|" + Integer.toString(rand.nextInt(199)) + "|" + Integer.toString(rand.nextInt(100)) + "|" + Integer.toString(rand.nextInt(50)));

                        break;
                    case "Walk simulation - Left foot":
                        int[] counter = {0};
                        int[] a = new int[]{0};
                        Timer t = new Timer();
                        t.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                a[0]++;
                                if (a[0] == 1)
                                    sendMessage("0|0|0|0|0|0|" + Integer.toString(rand.nextInt(100) + 1) + "|" + Integer.toString(rand.nextInt(100) + 1) + "|" + Integer.toString(rand.nextInt(100) + 1) + "|0|0|0|0|" + Integer.toString(rand.nextInt(100)) + "|" + Integer.toString(rand.nextInt(50)));
                                else {
                                    if (a[0] == 2)
                                        sendMessage(Integer.toString(rand.nextInt(300)) + "|" + Integer.toString(rand.nextInt(300)) + "|" + Integer.toString(rand.nextInt(300)) + "|" + Integer.toString(rand.nextInt(300)) + "|" + Integer.toString(rand.nextInt(300)) + "|" + Integer.toString(rand.nextInt(300)) + "|" + Integer.toString(rand.nextInt(300)) + "|" + Integer.toString(rand.nextInt(300)) + "|" + Integer.toString(rand.nextInt(300)) + "|" + Integer.toString(rand.nextInt(300)) + "|" + Integer.toString(rand.nextInt(300)) + "|" + Integer.toString(rand.nextInt(300)) + "|" + Integer.toString(rand.nextInt(300)) + "|" + Integer.toString(rand.nextInt(100)) + "|" + Integer.toString(rand.nextInt(50)));
                                    else {
                                        if (a[0] == 3)
                                            sendMessage(Integer.toString(rand.nextInt(299) + 1) + "|0|0|0|0|0|0|0|0|0|0|0|0|" + Integer.toString(rand.nextInt(100)) + "|" + Integer.toString(rand.nextInt(50)));
                                        else {
                                            sendMessage("0|0|0|0|0|0|0|0|0|0|0|0|0|" + Integer.toString(rand.nextInt(100)) + "|" + Integer.toString(rand.nextInt(50)));
                                            a[0] = 0;
                                            counter[0]++;
                                        }
                                    }
                                }
                                if (counter[0] == 40) {
                                    t.cancel();
                                }
                            }
                        }, 0, 200);

                        break;
                    case "Walk simulation - Right foot":
                        counter = new int[]{0};
                        a = new int[]{0};
                        t = new Timer();
                        t.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                a[0]++;
                                if (a[0] == 1)
                                    sendMessage(Integer.toString(rand.nextInt(299) + 1) + "|0|0|0|0|0|0|0|0|0|0|0|0|" + Integer.toString(rand.nextInt(100)) + "|" + Integer.toString(rand.nextInt(50)));
                                else {
                                    if (a[0] == 2)
                                        sendMessage("0|0|0|0|0|0|0|0|0|0|0|0|0|" + Integer.toString(rand.nextInt(100)) + "|" + Integer.toString(rand.nextInt(50)));
                                    else {
                                        if (a[0] == 3)
                                            sendMessage("0|0|0|0|0|0|" + Integer.toString(rand.nextInt(100) + 1) + "|" + Integer.toString(rand.nextInt(100) + 1) + "|" + Integer.toString(rand.nextInt(100) + 1) + "|0|0|0|0|" + Integer.toString(rand.nextInt(100)) + "|" + Integer.toString(rand.nextInt(50)));
                                        else {
                                            sendMessage(Integer.toString(rand.nextInt(300)) + "|" + Integer.toString(rand.nextInt(300)) + "|" + Integer.toString(rand.nextInt(300)) + "|" + Integer.toString(rand.nextInt(300)) + "|" + Integer.toString(rand.nextInt(300)) + "|" + Integer.toString(rand.nextInt(300)) + "|" + Integer.toString(rand.nextInt(300)) + "|" + Integer.toString(rand.nextInt(300)) + "|" + Integer.toString(rand.nextInt(300)) + "|" + Integer.toString(rand.nextInt(300)) + "|" + Integer.toString(rand.nextInt(300)) + "|" + Integer.toString(rand.nextInt(300)) + "|" + Integer.toString(rand.nextInt(300)) + "|" + Integer.toString(rand.nextInt(100)) + "|" + Integer.toString(rand.nextInt(50)));
                                            a[0] = 0;
                                            counter[0]++;
                                        }
                                    }
                                }
                                if (counter[0] == 40) {
                                    t.cancel();
                                }
                            }
                        }, 0, 200);
                        break;
                }

            }
        });


    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_ENABLE_BLUETOOTH:
                if (resultCode == Activity.RESULT_OK) {
                    chatController = new ChatController(this, handler);
                } else {
                    Toast.makeText(this, "Bluetooth still disabled, turn off application!", Toast.LENGTH_SHORT).show();
                    finish();
                }
        }
    }

    private void sendMessage(String message) {
        if (chatController.getState() != ChatController.STATE_CONNECTED) {
            Toast.makeText(this, "Connection was lost!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (message.length() > 0) {
            byte[] send = message.getBytes();
            chatController.write(send);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BLUETOOTH);
        } else {
            chatController = new ChatController(this, handler);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (chatController != null) {
            if (chatController.getState() == ChatController.STATE_NONE) {
                chatController.start();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (chatController != null)
            chatController.stop();
    }

    private final BroadcastReceiver discoveryFinishReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    discoveredDevicesAdapter.add(device.getName() + "\n" + device.getAddress());
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                if (discoveredDevicesAdapter.getCount() == 0) {
                    discoveredDevicesAdapter.add(getString(R.string.none_found));
                }
            }
        }
    };

    private Spinner setSpinner(Spinner spinner, int viewId, List<String> spinnerOptions, ArrayAdapter adapter, View view) {

        spinner = (Spinner) view.findViewById(viewId);
        adapter = new ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, spinnerOptions);
        spinner.setAdapter(adapter);

        return spinner;
    }
}