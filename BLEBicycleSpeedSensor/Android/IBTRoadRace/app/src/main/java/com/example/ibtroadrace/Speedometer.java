package com.example.ibtroadrace;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.widget.Toast;

import java.util.UUID;

public class Speedometer {
    private final static String TAG = "Speedometer";

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mBluetoothGatt;
    private String mBluetoothDeviceAddress;

    private enum State {
        STATE_DISCONNECTED,
        STATE_DISCONNECTING,
        STATE_CONNECTING,
        STATE_CONNECTED
    }

    State mConnectionState;

    private String MAC = "D6:3E:7A:BD:B5:7D";

    private static final UUID RFDUINO_RECEIVE_SVC =
            UUID.fromString("00002220-0000-1000-8000-00805f9b34fb");
    private static final UUID RFDUINO_RECEIVE_CHAR =
            UUID.fromString("00002221-0000-1000-8000-00805f9b34fb");
    private static final UUID RFDUINO_NOTIFY =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private static final int GATT_SUCCESS = 0;

    private int mpr;
    private int cnt;
    private long mprTotal;

    BluetoothGattCharacteristic mRFDuinoRecieveChar;

    boolean bNotifyOn = false;
    boolean bInitialRead = false;
    boolean setupComplete = false;

    static Context context;

    public Speedometer(Context context) {
        Speedometer.context = context;

        BluetoothManager mBluetoothManager = (BluetoothManager)
                context.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();

        if (!mBluetoothAdapter.isEnabled()) {
            toastMsg("BluetoothAdapter not enabled");
            return;
        }
    }

    public void start() {
        Log.v(TAG,"start()");
        if (mBluetoothAdapter.isEnabled()) {
            connect(MAC);
        }
    }

    public void stop() {
        Log.v(TAG,"stop()");
        disconnect();
    }

    public int getMpr() {
        return mpr;
    }

    public int getCnt() {
        return cnt;
    }

    public long getMprTotal(){
        return mprTotal;
    }

    public void resetMprTotal() {
        mprTotal = 0;
    }

    public boolean getSetupComplete() {
        return setupComplete;
    }

    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            toastMsg("BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            toastMsg("Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = State.STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            toastMsg("Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(context, false, mGattCallback);
        toastMsg("Creating a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = State.STATE_CONNECTING;
        return true;
    }

    private void disconnect() {
        mConnectionState = State.STATE_DISCONNECTING;
        if (mBluetoothGatt != null) {
            if (bNotifyOn) {
                mBluetoothGatt.setCharacteristicNotification(mRFDuinoRecieveChar, false);
                BluetoothGattDescriptor descriptor =
                        mRFDuinoRecieveChar.getDescriptor(RFDUINO_NOTIFY);
                descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                mBluetoothGatt.writeDescriptor(descriptor);
            }
            bNotifyOn = false;
            mBluetoothGatt.disconnect();
        }
    }

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                mConnectionState = State.STATE_CONNECTED;
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                if (mConnectionState == State.STATE_DISCONNECTING) {
                    mConnectionState = State.STATE_DISCONNECTED;
                    mBluetoothGatt.close();
                    mBluetoothGatt = null;
                } else if (mConnectionState == State.STATE_CONNECTED) {
                    Log.v(TAG, "BLE Dropped, trying to reconnect");
                    mBluetoothGatt.close();
                    mBluetoothGatt = null;
                    connect(MAC);
                } else {
                    Log.v(TAG, "BLE Dropped, failed to reconnect");
                }
            }
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, final int status) {
            mRFDuinoRecieveChar =
                    gatt.getService(RFDUINO_RECEIVE_SVC).getCharacteristic(RFDUINO_RECEIVE_CHAR);
            mBluetoothGatt.setCharacteristicNotification(mRFDuinoRecieveChar, true);
            BluetoothGattDescriptor descriptor = mRFDuinoRecieveChar.getDescriptor(RFDUINO_NOTIFY);
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);
            bNotifyOn = true;
            bInitialRead = true;
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic, int status) {
            if ((characteristic.getValue() != null) && (status == BluetoothGatt.GATT_SUCCESS)) {
                byte b[] = characteristic.getValue();
                cnt = b[0] & 0xFF | (b[1] & 0xFF) << 8 | (b[2] & 0xFF) << 16 | (b[3] & 0xFF) << 24;
                mpr = b[4] & 0xFF | (b[5] & 0xFF) << 8 | (b[6] & 0xFF) << 16 | (b[7] & 0xFF) << 24;
                mprTotal = mpr + mprTotal;
                setupComplete = true;
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            final BluetoothGattCharacteristic characteristic) {
            if ((characteristic.getValue() != null)) {
                byte b[] = characteristic.getValue();
                cnt = b[0] & 0xFF | (b[1] & 0xFF) << 8 | (b[2] & 0xFF) << 16 | (b[3] & 0xFF) << 24;
                mpr = b[4] & 0xFF | (b[5] & 0xFF) << 8 | (b[6] & 0xFF) << 16 | (b[7] & 0xFF) << 24;
                mprTotal = mpr + mprTotal;
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt,
                                      BluetoothGattDescriptor descriptor, int status) {
            if (status == GATT_SUCCESS) {
                if (bInitialRead) {
                    mBluetoothGatt.readCharacteristic(mRFDuinoRecieveChar);
                    bInitialRead = false;
                }
            } else {
                toastMsg("Unable to set notify on BLE device.");
            }
        }
    };

    public void updateSpeedometer(final float speed, final float miles, final long rideTime,
                                  final int score, final int heading) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            public void run() {
                float avgSpeed;

                int seconds = (int) (rideTime / 1000) % 60;
                int minutes = (int) ((rideTime / (1000 * 60)) % 60);
                int hours = (int) ((rideTime / (1000 * 60 * 60)) % 24);

                if (rideTime < 1000) {
                    avgSpeed = 0;
                } else {
                    avgSpeed = miles / (rideTime / 1000) * 3600;
                }

                MainActivity.tvRideStats.setText(String.format(
                        "MPH:     %5.2f SCORE: %5d\nAvg Spd: %5.2f HEADING: %3d\n" +
                        "Miles:   %5.2f\nTime:    %02d:%02d:%02d",
                        speed, score, avgSpeed, heading, miles, hours, minutes, seconds));
            }
        });
    }

    private void toastMsg(String msg) {
        Toast toast = Toast.makeText(context, msg, Toast.LENGTH_LONG);
        toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0);
        toast.show();
    }

}