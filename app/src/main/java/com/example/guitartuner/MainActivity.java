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
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final long SCAN_PERIOD = 10000;

    private static final UUID SERVICE_UUID = UUID.fromString("00001523-0000-1000-8000-00805f9b34fb");
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("00001524-0000-1000-8000-00805f9b34fb");
    private static final UUID CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private static final double[] guitarNotes = {82.41, 110.00, 146.83, 196.00, 246.94, 329.63};
    private static final String[] noteNames = {"E2", "A2", "D3", "G3", "B3", "E4"};

    private long lastDetectionTime = 0;
    private static final long PERSISTENCE_MS = 1500;

    private Button btnConnect;
    private TextView statusText;
    private TextView noteText;
    private TextView freqText;
    private TextView rawLogsText;
    private ScrollView rawLogsScroll;
    private View tunerPointer;
    private LinearLayout waterfallContainer;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt;
    private boolean isScanning = false;
    private boolean isConnected = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private double smoothedCents = 0;
    private static final double SMOOTHING_FACTOR = 0.25; // Nižje = bolj gladko, a počasneje

    private final Runnable watchdogRunnable = new Runnable() {
        @Override
        public void run() {
            if (isConnected) {
                runOnUiThread(() -> {
                    long now = System.currentTimeMillis();
                    if (now - lastDetectionTime > PERSISTENCE_MS) {
                        statusText.setText("STRIKE A STRING");
                        statusText.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.textColorSecondary));
                        noteText.setText("--");
                        freqText.setText("0.00 Hz");
                        tunerPointer.setVisibility(View.INVISIBLE);
                        smoothedCents = 0;
                    }
                    // Vedno dodamo prazno vrstico v slap, če ni bilo signala zadnjih 100ms
                    if (now - lastDetectionTime > 100) {
                        addWaterfallLine(0, null);
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

        btnConnect = findViewById(R.id.btnConnect);
        statusText = findViewById(R.id.statusText);
        noteText = findViewById(R.id.noteText);
        freqText = findViewById(R.id.freqText);
        rawLogsText = findViewById(R.id.rawLogsText);
        rawLogsScroll = findViewById(R.id.rawLogsScroll);
        tunerPointer = findViewById(R.id.tunerPointer);
        waterfallContainer = findViewById(R.id.waterfallContainer);

        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth ni podprt", Toast.LENGTH_LONG).show();
            btnConnect.setEnabled(false);
        } else {
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        }

        btnConnect.setOnClickListener(v -> {
            if (isConnected) {
                disconnectDevice();
            } else {
                if (checkAndRequestPermissions()) {
                    startScan();
                }
            }
        });

        handler.post(watchdogRunnable);
    }

    private void appendLog(String text) {
        runOnUiThread(() -> {
            String currentText = rawLogsText.getText().toString();
            if (currentText.length() > 500) currentText = currentText.substring(250);
            rawLogsText.setText(currentText + "\n" + text);
            rawLogsScroll.post(() -> rawLogsScroll.fullScroll(ScrollView.FOCUS_DOWN));
        });
    }

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
            statusText.setText("Iskanje XIAO...");
            appendLog("Iščem XIAO...");
            bluetoothLeScanner.startScan(leScanCallback);
            btnConnect.setText("Prekini");
            handler.postDelayed(() -> { if (isScanning) stopScan(); }, SCAN_PERIOD);
        }
    }

    @SuppressLint("MissingPermission")
    private void stopScan() {
        if (isScanning) {
            isScanning = false;
            bluetoothLeScanner.stopScan(leScanCallback);
            if (!isConnected) {
                statusText.setText("Iskanje ustavljeno");
                btnConnect.setText("Poveži z XIAO");
            }
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
        statusText.setText("Povezovanje...");
        bluetoothGatt = device.connectGatt(this, false, gattCallback);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                isConnected = true;
                runOnUiThread(() -> {
                    statusText.setText("Povezano!");
                    btnConnect.setText("Odklopi");
                    btnConnect.setBackgroundTintList(ContextCompat.getColorStateList(MainActivity.this, android.R.color.holo_red_dark));
                });
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                isConnected = false;
                runOnUiThread(() -> {
                    statusText.setText("Prekinjeno");
                    btnConnect.setText("Poveži z XIAO");
                    btnConnect.setBackgroundTintList(ContextCompat.getColorStateList(MainActivity.this, R.color.accentColor));
                    noteText.setText("--");
                    freqText.setText("0.00 Hz");
                    tunerPointer.setVisibility(View.INVISIBLE);
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
                            appendLog("Naročen na podatke.");
                        }
                    }
                }
            }
        }

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
                        runOnUiThread(() -> updateUI(finalFreq));
                    }
                }
            }
        }
    };

    private void updateUI(double frequency) {
        long now = System.currentTimeMillis();

        if (frequency > 65 && frequency < 450) {
            lastDetectionTime = now;
            freqText.setText(String.format(Locale.US, "%.2f Hz", frequency));

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
                noteText.setText(noteNames[closestIndex]);
                double targetFreq = guitarNotes[closestIndex];
                double cents = 1200 * Math.log(frequency / targetFreq) / Math.log(2);
                
                // Low-pass filter za glajenje centov
                smoothedCents = smoothedCents + SMOOTHING_FACTOR * (cents - smoothedCents);
                updateScale(smoothedCents);

                if (Math.abs(cents) < 2) {
                    statusText.setText("PERFECT");
                    statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light));
                } else if (cents > 0) {
                    statusText.setText(String.format(Locale.US, "+%.1f cents", cents));
                    statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light));
                } else {
                    statusText.setText(String.format(Locale.US, "%.1f cents", cents));
                    statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light));
                }
            } else {
                noteText.setText("--");
                statusText.setText("CHECK STRING");
                statusText.setTextColor(ContextCompat.getColor(this, R.color.textColorSecondary));
                // Ne skrivamo takoj, pustimo da watchdog opravi svoje
            }
        }
    }

    private void updateScale(double cents) {
        tunerPointer.setVisibility(View.VISIBLE);
        
        double clampedCents = Math.max(-50, Math.min(50, cents));
        View parent = (View) tunerPointer.getParent();
        int width = parent.getWidth();
        if (width == 0) return;

        float x = (float) ((clampedCents / 100.0) * width);
        
        // Namesto setTranslationX uporabimo animate() za bolj tekoče premikanje
        tunerPointer.animate()
                .translationX(x)
                .setDuration(50)
                .start();

        addWaterfallLine(x, clampedCents);
    }

    private void addWaterfallLine(float translationX, Double cents) {
        int lineHeight = 10;
        
        FrameLayout wrapper = new FrameLayout(this);
        wrapper.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, lineHeight));

        if (cents != null) {
            View dot = new View(this);
            int dotSize = 10;
            FrameLayout.LayoutParams dotLp = new FrameLayout.LayoutParams(dotSize, dotSize);
            dotLp.gravity = Gravity.CENTER_VERTICAL;
            dot.setLayoutParams(dotLp);
            
            if (Math.abs(cents) < 2) {
                dot.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_light));
            } else {
                dot.setBackgroundColor(ContextCompat.getColor(this, android.R.color.white));
            }

            dot.setTranslationX(((View)waterfallContainer).getWidth() / 2f + translationX - (dotSize / 2f));
            wrapper.addView(dot);
        }

        waterfallContainer.addView(wrapper, 0);

        if (waterfallContainer.getChildCount() > 20) {
            waterfallContainer.removeViewAt(waterfallContainer.getChildCount() - 1);
        }

        for (int i = 0; i < waterfallContainer.getChildCount(); i++) {
            float alpha = 1.0f - (i / (float) waterfallContainer.getChildCount());
            waterfallContainer.getChildAt(i).setAlpha(alpha);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startScan();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(watchdogRunnable);
        if (bluetoothGatt != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                bluetoothGatt.disconnect();
                bluetoothGatt.close();
            }
        }
    }
}
