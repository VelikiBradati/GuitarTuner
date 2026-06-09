package com.example.guitartuner;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements TunerFragment.TunerInterface {

    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final long SCAN_PERIOD = 10000;

    private static final UUID SERVICE_UUID = UUID.fromString("00001523-0000-1000-8000-00805f9b34fb");
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("00001524-0000-1000-8000-00805f9b34fb");
    private static final UUID CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private static final double[] guitarNotes = {82.41, 110.00, 146.83, 196.00, 246.94, 329.63};
    private static final String[] noteNames = {"E2", "A2", "D3", "G3", "B3", "E4"};

    private long lastDetectionTime = 0;
    private static final long PERSISTENCE_MS = 1500;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt;
    private boolean isScanning = false;
    private boolean isConnected = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private double smoothedCents = 0;
    private static final double SMOOTHING_FACTOR = 0.25;

    private TunerFragment tunerFragment;
    private SongbookFragment songbookFragment;
    private BottomNavigationView bottomNav;

    private final Runnable watchdogRunnable = new Runnable() {
        @Override
        public void run() {
            if (isConnected) {
                runOnUiThread(() -> {
                    long now = System.currentTimeMillis();
                    if (now - lastDetectionTime > PERSISTENCE_MS) {
                        if (tunerFragment != null && tunerFragment.isVisible()) {
                            tunerFragment.setStatusText("STRIKE A STRING");
                            tunerFragment.setStatusTextColor(android.R.color.darker_gray);
                            tunerFragment.setNoteText("--");
                            tunerFragment.setFreqText("0.00 Hz");
                            tunerFragment.updateScale(null);
                        }
                        smoothedCents = 0;
                    } else if (now - lastDetectionTime > 100) {
                        if (tunerFragment != null && tunerFragment.isVisible()) {
                            tunerFragment.updateScale(null);
                        }
                    }
                });
            }
            handler.postDelayed(this, 100);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bottomNav = findViewById(R.id.bottom_navigation);
        
        tunerFragment = new TunerFragment();
        tunerFragment.setTunerInterface(this);
        songbookFragment = new SongbookFragment();

        loadFragment(tunerFragment);

        bottomNav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_tuner) {
                loadFragment(tunerFragment);
                return true;
            } else if (item.getItemId() == R.id.nav_songbook) {
                loadFragment(songbookFragment);
                return true;
            }
            return false;
        });

        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter != null) {
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        }

        handler.post(watchdogRunnable);
    }

    public void setBottomNavVisibility(int visibility) {
        if (bottomNav != null) {
            bottomNav.setVisibility(visibility);
        }
    }

    public void switchToTuner() {
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_tuner);
        }
        loadFragment(tunerFragment);
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    @Override
    public void onConnectClicked() {
        if (isConnected) {
            disconnectDevice();
        } else {
            if (checkAndRequestPermissions()) {
                startScan();
            }
        }
    }

    @Override
    public boolean isConnected() {
        return isConnected;
    }

    // preveri dovoljenja
    private boolean checkAndRequestPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN);
            permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        List<String> listPermissionsNeeded = new ArrayList<>();
        for (String perm : permissionsNeeded) {
            if (ActivityCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(perm);
            }
        }

        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
            return false;
        }
        return true;
    }

    @SuppressLint("MissingPermission")
    private void startScan() {
        if (!isScanning) {
            isScanning = true;
            if (tunerFragment != null && tunerFragment.isAdded()) {
                tunerFragment.setStatusText("Iskanje XIAO...");
                tunerFragment.appendLog("Iščem XIAO...");
            }
            bluetoothLeScanner.startScan(leScanCallback);
            handler.postDelayed(() -> { if (isScanning) stopScan(); }, SCAN_PERIOD);
        }
    }

    @SuppressLint("MissingPermission")
    private void stopScan() {
        if (isScanning) {
            isScanning = false;
            bluetoothLeScanner.stopScan(leScanCallback);
        }
    }

    @SuppressLint("MissingPermission")
    private void disconnectDevice() {
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
        }
    }

    private final ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            @SuppressLint("MissingPermission")
            String deviceName = result.getDevice().getName();
            if (deviceName != null && deviceName.contains("XIAO")) {
                stopScan();
                connectToDevice(result.getDevice());
            }
        }
    };

    @SuppressLint("MissingPermission")
    private void connectToDevice(BluetoothDevice device) {
        if (tunerFragment != null && tunerFragment.isAdded()) tunerFragment.setStatusText("Povezovanje...");
        bluetoothGatt = device.connectGatt(this, false, gattCallback);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                isConnected = true;
                runOnUiThread(() -> {
                    if (tunerFragment != null && tunerFragment.isAdded()) {
                        tunerFragment.setStatusText("Povezano!");
                        tunerFragment.updateConnectButton(true);
                    }
                });
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                isConnected = false;
                runOnUiThread(() -> {
                    if (tunerFragment != null && tunerFragment.isAdded()) {
                        tunerFragment.setStatusText("Prekinjeno");
                        tunerFragment.updateConnectButton(false);
                        tunerFragment.setNoteText("--");
                        tunerFragment.setFreqText("0.00 Hz");
                        tunerFragment.updateScale(null);
                    }
                });
                if (bluetoothGatt != null) {
                    bluetoothGatt.close();
                    bluetoothGatt = null;
                }
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(SERVICE_UUID);
                if (service != null) {
                    BluetoothGattCharacteristic characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
                    if (characteristic != null) {
                        gatt.setCharacteristicNotification(characteristic, true);
                        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
                        if (descriptor != null) {
                            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            gatt.writeDescriptor(descriptor);
                        }
                    }
                }
            }
        }

        // iz 4 bajtov sestavi float decimalno število
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                byte[] data = characteristic.getValue();
                if (data != null) {
                    double freq = 0;
                    if (data.length >= 4) {
                        freq = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getFloat();
                    } else if (data.length >= 2) {
                        int raw = (data[0] & 0xFF) | ((data[1] & 0xFF) << 8);
                        freq = (double) raw;
                    }

                    if (freq > 0) {
                        double finalFreq = freq;
                        runOnUiThread(() -> processFrequency(finalFreq));
                    }
                }
            }
        }
    };

    private void processFrequency(double frequency) {
        if (frequency <= 65 || frequency >= 450) return;
        lastDetectionTime = System.currentTimeMillis();

        int closestIndex = 0;
        double minDiff = Double.MAX_VALUE;
        for (int i = 0; i < guitarNotes.length; i++) {
            double diff = Math.abs(frequency - guitarNotes[i]);
            if (diff < minDiff) {
                minDiff = diff;
                closestIndex = i;
            }
        }

        if (minDiff < 20) {
            double targetFreq = guitarNotes[closestIndex];
            double cents = 1200 * Math.log(frequency / targetFreq) / Math.log(2);
            smoothedCents = smoothedCents + SMOOTHING_FACTOR * (cents - smoothedCents);

            if (tunerFragment != null && tunerFragment.isVisible()) {
                tunerFragment.setFreqText(String.format(Locale.US, "%.2f Hz", frequency));
                tunerFragment.setNoteText(noteNames[closestIndex]);
                tunerFragment.updateScale(smoothedCents);

                if (Math.abs(cents) < 2) {
                    tunerFragment.setStatusText("PERFECT");
                    tunerFragment.setStatusTextColor(android.R.color.holo_green_light);
                } else {
                    tunerFragment.setStatusText(String.format(Locale.US, "%+.1f cents", cents));
                    tunerFragment.setStatusTextColor(android.R.color.holo_red_light);
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(watchdogRunnable);
        if (bluetoothGatt != null) {
            disconnectDevice();
        }
    }
}
