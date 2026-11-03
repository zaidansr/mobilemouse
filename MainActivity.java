package app.pocketpad.mouse;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private static final byte MOUSE_REPORT_ID = 2;
    private static final byte[] DESCRIPTOR = {
        0x05, 0x01, 0x09, 0x02, (byte) 0xA1, 0x01,
        (byte) 0x85, MOUSE_REPORT_ID,
        0x09, 0x01, (byte) 0xA1, 0x00,
        0x05, 0x09, 0x19, 0x01, 0x29, 0x03, 0x15, 0x00, 0x25, 0x01,
        0x75, 0x01, (byte) 0x95, 0x03, (byte) 0x81, 0x02,
        0x75, 0x05, (byte) 0x95, 0x01, (byte) 0x81, 0x03,
        0x05, 0x01, 0x09, 0x30, 0x09, 0x31, 0x09, 0x38,
        0x15, (byte) 0x81, 0x25, 0x7F, 0x75, 0x08, (byte) 0x95, 0x03,
        (byte) 0x81, 0x06, (byte) 0xC0, (byte) 0xC0
    };
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BluetoothAdapter adapter;
    private BluetoothHidDevice hid;
    private BluetoothDevice host;
    private boolean ready, registering, opening, bootProtocol;
    private int held;
    private TextView status;
    private Button pair, connect;
    private float lastX, lastY, carryX, carryY, travel;
    private boolean scrolled;
    private final BroadcastReceiver bluetoothState = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
            if (state == BluetoothAdapter.STATE_ON) startMouse();
            if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF) {
                ready = registering = opening = false;
                clearHost();
                show("Bluetooth is off. Tap Turn on.");
            }
        }
    };
    private final BluetoothProfile.ServiceListener service = new BluetoothProfile.ServiceListener() {
        @Override public void onServiceConnected(int profile, BluetoothProfile proxy) {
            opening = false;
            if (isDestroyed()) {
                if (permitted()) adapter.closeProfileProxy(profile, proxy);
                return;
            }
            hid = (BluetoothHidDevice) proxy;
            startMouse();
        }
        @Override public void onServiceDisconnected(int profile) {
            hid = null;
            ready = registering = opening = false;
            clearHost();
            show("Bluetooth mouse service disconnected.");
        }
    };
    private final BluetoothHidDevice.Callback callback = new BluetoothHidDevice.Callback() {
        @Override public void onAppStatusChanged(BluetoothDevice device, boolean registered) {
            registering = false;
            ready = registered;
            if (!registered) clearHost();
            show(ready ? "Ready. Pair a laptop or choose a paired device."
                       : "Mouse profile stopped. Keep mobilemouse open and tap Retry.");
        }
        @Override public void onConnectionStateChanged(BluetoothDevice device, int state) {
            if (state == BluetoothProfile.STATE_CONNECTED && permitted() && ready) {
                if (host != null && !host.equals(device)) {
                    hid.disconnect(device);
                    return;
                }
                host = device;
                show("Connected to " + name(device));
            } else if (state == BluetoothProfile.STATE_DISCONNECTED && (host == null || host.equals(device))) {
                clearHost();
                show(ready ? "Disconnected. Pair or choose a paired laptop." : "Tap Retry to start the mouse.");
            }
        }
        @Override public void onSetProtocol(BluetoothDevice device, byte protocol) {
            bootProtocol = protocol == BluetoothHidDevice.PROTOCOL_BOOT_MODE;
        }
        @Override public void onGetReport(BluetoothDevice device, byte type, byte id, int size) {
            if (hid == null || !permitted()) return;
            int length = bootProtocol ? 3 : 4;
            if (type == BluetoothHidDevice.REPORT_TYPE_INPUT && id == MOUSE_REPORT_ID && (size == 0 || size >= length)) {
                hid.replyReport(device, type, id, payload(0, 0, 0));
            } else {
                hid.reportError(device, BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID);
            }
        }
        @Override public void onSetReport(BluetoothDevice device, byte type, byte id, byte[] data) {
            if (hid != null && permitted()) hid.reportError(device, BluetoothHidDevice.ERROR_RSP_UNSUPPORTED_REQ);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        adapter = manager == null ? null : manager.getAdapter();
        buildUi();
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(bluetoothState, filter, Context.RECEIVER_EXPORTED);
        else registerReceiver(bluetoothState, filter);
        startMouse();
    }

    @Override protected void onResume() {
        super.onResume();
        startMouse();
    }

    @Override protected void onPause() {
        if (held != 0) setButtons(0);
        super.onPause();
    }

    @Override protected void onDestroy() {
        unregisterReceiver(bluetoothState);
        if (hid != null && permitted()) {
            hid.unregisterApp();
            adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid);
        }
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private boolean permitted() {
        return Build.VERSION.SDK_INT < 31 ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private void startMouse() {
        if (adapter == null) { show("This phone has no Bluetooth adapter."); return; }
        if (!permitted()) { show("Allow Nearby devices to start the mouse."); return; }
        if (!adapter.isEnabled()) { show("Bluetooth is off. Tap Turn on."); return; }
        if (ready || registering || opening) return;
        if (hid == null) {
            opening = adapter.getProfileProxy(this, service, BluetoothProfile.HID_DEVICE);
            show(opening ? "Starting Bluetooth mouse…" : "Bluetooth HID Device is unavailable on this phone.");
            handler.postDelayed(() -> {
                if (opening && hid == null) {
                    opening = false;
                    show("Bluetooth HID Device did not start. Tap Retry.");
                }
            }, 8000);
            return;
        }
        BluetoothHidDeviceAppSdpSettings sdp = new BluetoothHidDeviceAppSdpSettings(
            "mobilemouse", "Bluetooth touchpad", "mobilemouse", BluetoothHidDevice.SUBCLASS1_MOUSE, DESCRIPTOR);
        registering = hid.registerApp(sdp, null, null, command -> handler.post(() -> {
            if (!isDestroyed()) command.run();
        }), callback);
        show(registering ? "Registering Bluetooth mouse…" : "Mouse profile registration failed. Tap Retry.");
    }

    private void show(String message) {
        status.setText(message);
        pair.setEnabled(adapter != null);
        pair.setText(!permitted() ? "Allow nearby devices" :
            adapter != null && !adapter.isEnabled() ? "Turn on Bluetooth" :
            ready ? "Pair new laptop" : "Retry");
        connect.setEnabled(ready);
        connect.setText(host == null ? "Paired devices" : "Disconnect");
    }

    private void pair() {
        if (!permitted()) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 1);
        } else if (!adapter.isEnabled()) {
            startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), 3);
        } else if (!ready) {
            startMouse();
        } else if (Build.VERSION.SDK_INT >= 31 &&
                   checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_ADVERTISE}, 2);
        } else {
            discoverable();
        }
    }

    private void discoverable() {
        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120);
        startActivityForResult(intent, 4);
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants);
        if (grants.length == 0 || grants[0] != PackageManager.PERMISSION_GRANTED) {
            show("Allow Nearby devices in Android app settings to use this action.");
        } else if (request == 2 && ready) discoverable();
        else startMouse();
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 4 && result > 0 && host == null) {
            show("On your laptop, add a Bluetooth device and select this phone.");
        }
        if (request == 3) startMouse();
    }

    private String name(BluetoothDevice device) {
        String label = device.getName();
        return label == null || label.isEmpty() ? device.getAddress() : label;
    }

    private void pairedDevices() {
        if (!ready || !permitted()) return;
        if (host != null) {
            setButtons(0);
            if (!hid.disconnect(host)) show("Could not disconnect. Try again.");
            return;
        }
        List<BluetoothDevice> devices = new ArrayList<>(adapter.getBondedDevices());
        devices.sort((a, b) -> name(a).compareToIgnoreCase(name(b)));
        if (devices.isEmpty()) { show("No paired devices. Tap Pair new laptop."); return; }
        String[] names = new String[devices.size()];
        for (int i = 0; i < names.length; i++) names[i] = name(devices.get(i));
        new AlertDialog.Builder(this).setTitle("Choose your laptop")
            .setItems(names, (dialog, index) -> {
                if (!ready || host != null || !permitted()) return;
                BluetoothDevice device = devices.get(index);
                if (hid.connect(device)) {
                    show("Connecting to " + name(device) + "…");
                    handler.postDelayed(() -> {
                        if (ready && host == null && status.getText().toString().startsWith("Connecting")) {
                            show("Connection timed out. Check Bluetooth on the laptop and try again.");
                        }
                    }, 12000);
                } else show("Could not connect. Check Bluetooth on the laptop and try again.");
            }).setNegativeButton("Cancel", null).show();
    }

    private void clearHost() { host = null; held = 0; bootProtocol = false; }

    private byte[] payload(int x, int y, int wheel) {
        if (bootProtocol) return new byte[]{(byte) held, (byte) x, (byte) y};
        return new byte[]{(byte) held, (byte) x, (byte) y, (byte) wheel};
    }

    private void send(int x, int y, int wheel) {
        if (host == null || hid == null || !permitted()) return;
        do {
            int dx = Math.max(-127, Math.min(127, x));
            int dy = Math.max(-127, Math.min(127, y));
            int dw = Math.max(-127, Math.min(127, wheel));
            if (!hid.sendReport(host, MOUSE_REPORT_ID, payload(dx, dy, dw))) return;
            x -= dx;
            y -= dy;
            wheel -= dw;
        } while (x != 0 || y != 0 || wheel != 0);
    }

    private void setButtons(int buttons) {
        if (held == buttons) return;
        held = buttons;
        send(0, 0, 0);
    }

    private void click(int mask) {
        int previous = held;
        setButtons(previous | mask);
        setButtons(previous);
    }

    private void anchor(MotionEvent event, int excluded) {
        lastX = lastY = 0;
        int count = 0;
        for (int i = 0; i < event.getPointerCount(); i++) {
            if (i == excluded) continue;
            lastX += event.getX(i);
            lastY += event.getY(i);
            count++;
        }
        if (count > 0) { lastX /= count; lastY /= count; }
    }

    private boolean trackpad(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                travel = carryX = carryY = 0;
                scrolled = false;
                anchor(event, -1);
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_POINTER_UP:
                scrolled = true;
                carryX = carryY = 0;
                anchor(event, event.getActionMasked() == MotionEvent.ACTION_POINTER_UP ? event.getActionIndex() : -1);
                break;
            case MotionEvent.ACTION_MOVE:
                float oldX = lastX, oldY = lastY;
                anchor(event, -1);
                float dx = (lastX - oldX) / getResources().getDisplayMetrics().density;
                float dy = (lastY - oldY) / getResources().getDisplayMetrics().density;
                travel += Math.abs(dx) + Math.abs(dy);
                if (event.getPointerCount() >= 2) {
                    carryY -= dy / 14f;
                    int ticks = (int) carryY;
                    carryY -= ticks;
                    if (ticks != 0) send(0, 0, ticks);
                } else {
                    carryX += dx * 1.65f;
                    carryY += dy * 1.65f;
                    int x = (int) carryX, y = (int) carryY;
                    carryX -= x;
                    carryY -= y;
                    if (x != 0 || y != 0) send(x, y, 0);
                }
                break;
            case MotionEvent.ACTION_UP:
                if (!scrolled && travel < 8 && event.getEventTime() - event.getDownTime() < 250) view.performClick();
                break;
            default:
                break;
        }
        return true;
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private TextView label(String text, int size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private Button button(String text) {
        Button view = new Button(this);
        view.setText(text);
        view.setAllCaps(false);
        return view;
    }

    private void addMouseButton(LinearLayout row, String label, int mask) {
        Button button = button(label);
        button.setOnClickListener(view -> click(mask));
        button.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: setButtons(held | mask); break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: setButtons(held & ~mask); break;
                default: break;
            }
            return true;
        });
        row.addView(button, new LinearLayout.LayoutParams(0, dp(56), 1));
    }

    private void buildUi() {
        int ink = Color.rgb(236, 243, 252);
        int muted = Color.rgb(173, 192, 215);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(14, 22, 34));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(dp(20) + insets.getSystemWindowInsetLeft(), dp(14) + insets.getSystemWindowInsetTop(),
                dp(20) + insets.getSystemWindowInsetRight(), dp(12) + insets.getSystemWindowInsetBottom());
            return insets;
        });
        TextView title = label("mobilemouse", 32, ink);
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        root.addView(title);
        root.addView(label("BLUETOOTH MOUSE", 12, muted));
        status = label("", 15, ink);
        status.setPadding(0, dp(10), 0, dp(4));
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        root.addView(status);
        pair = button("Pair new laptop");
        pair.setOnClickListener(view -> pair());
        root.addView(pair);
        connect = button("Paired devices");
        connect.setOnClickListener(view -> pairedDevices());
        root.addView(connect);
        TextView pad = label("TOUCHPAD\n\nSlide to move  •  Tap to click\nTwo fingers to scroll", 15, muted);
        pad.setGravity(Gravity.CENTER);
        GradientDrawable surface = new GradientDrawable();
        surface.setColor(Color.rgb(25, 41, 59));
        surface.setCornerRadius(dp(22));
        surface.setStroke(dp(1), Color.rgb(54, 77, 102));
        pad.setBackground(surface);
        pad.setOnClickListener(view -> click(1));
        pad.setOnTouchListener(this::trackpad);
        LinearLayout.LayoutParams padParams = new LinearLayout.LayoutParams(-1, 0, 1);
        padParams.setMargins(0, dp(10), 0, dp(6));
        root.addView(pad, padParams);
        LinearLayout row = new LinearLayout(this);
        row.setMotionEventSplittingEnabled(true);
        root.setMotionEventSplittingEnabled(true);
        addMouseButton(row, "Left click", 1);
        addMouseButton(row, "Middle", 4);
        addMouseButton(row, "Right click", 2);
        root.addView(row);
        setContentView(root);
    }
}
