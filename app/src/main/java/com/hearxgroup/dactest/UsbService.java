package com.hearxgroup.dactest;

import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import com.felhr.usbserial.UsbSerialInterface;
import com.felhr.utils.SafeUsbRequest;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class UsbService extends Service {

    public static final String TAG = "UsbService";

    public static final String ACTION_USB_READY = "com.felhr.connectivityservices.USB_READY";
    public static final String ACTION_USB_ATTACHED = "android.hardware.usb.action.USB_DEVICE_ATTACHED";
    public static final String ACTION_USB_DETACHED = "android.hardware.usb.action.USB_DEVICE_DETACHED";
    public static final String ACTION_USB_NOT_SUPPORTED = "com.felhr.usbservice.USB_NOT_SUPPORTED";
    public static final String ACTION_NO_USB = "com.felhr.usbservice.NO_USB";
    public static final String ACTION_USB_PERMISSION_GRANTED = "com.felhr.usbservice.USB_PERMISSION_GRANTED";
    public static final String ACTION_USB_PERMISSION_NOT_GRANTED = "com.felhr.usbservice.USB_PERMISSION_NOT_GRANTED";
    public static final String ACTION_USB_DISCONNECTED = "com.felhr.usbservice.USB_DISCONNECTED";
    public static final String ACTION_CDC_DRIVER_NOT_WORKING = "com.felhr.connectivityservices.ACTION_CDC_DRIVER_NOT_WORKING";
    public static final String ACTION_USB_DEVICE_NOT_WORKING = "com.felhr.connectivityservices.ACTION_USB_DEVICE_NOT_WORKING";
    public static final int MESSAGE_FROM_SERIAL_PORT = 0;
    public static final int CTS_CHANGE = 1;
    public static final int DSR_CHANGE = 2;
    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    private static final int BAUD_RATE = 9600; // BaudRate. Change this value if you need
    public static boolean SERVICE_CONNECTED = false;

    int[] commandMinus5dB = new int[]{42 ,42 ,0 ,13 ,212 ,0 ,1 ,16 ,0 ,3 ,1 ,3 ,23 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0};

    private IBinder binder = new UsbBinder();

    private Context context;
    private Handler mHandler;
    private UsbManager usbManager;
    private UsbDevice device;
    private boolean readCallbackSet = false;
    private UsbDeviceConnection connection;
    private CP2615SerialDevice serialPort;

    private boolean serialPortConnected;
    /*
     *  Data received from serial port will be received here. Just populate onReceivedData with your code
     *  In this particular example. byte stream is converted to String and send to UI thread to
     *  be treated there.
     */
    private UsbSerialInterface.UsbReadCallback mCallback = new UsbSerialInterface.UsbReadCallback() {
        @Override
        public void onReceivedData(byte[] arg0) {
            try {
                Log.d(TAG, "onReceivedData");
                int[] intArr = new int[arg0.length];
                for(int k=0; k<arg0.length; k++) {
                    intArr[k] = (int)arg0[k];
                    //Log.d(TAG, "byteEntry="+(int)byteEntry);
                }
                String convertedData = AltConverter.convertToString(intArr);
                Log.d(TAG, "convertedData="+convertedData);
                String data = new String(arg0, "UTF-8");
                Log.d(TAG, "data="+data);
                if (mHandler != null)
                    mHandler.obtainMessage(MESSAGE_FROM_SERIAL_PORT, convertedData).sendToTarget();
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
    };

    /*
     * State changes in the CTS line will be received here
     */
    private UsbSerialInterface.UsbCTSCallback ctsCallback = new UsbSerialInterface.UsbCTSCallback() {
        @Override
        public void onCTSChanged(boolean state) {
            if(mHandler != null)
                mHandler.obtainMessage(CTS_CHANGE).sendToTarget();
        }
    };

    /*
     * State changes in the DSR line will be received here
     */
    private UsbSerialInterface.UsbDSRCallback dsrCallback = new UsbSerialInterface.UsbDSRCallback() {
        @Override
        public void onDSRChanged(boolean state) {
            if(mHandler != null)
                mHandler.obtainMessage(DSR_CHANGE).sendToTarget();
        }
    };
    /*
     * Different notifications from OS will be received here (USB attached, detached, permission responses...)
     * About BroadcastReceiver: http://developer.android.com/reference/android/content/BroadcastReceiver.html
     */
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context arg0, Intent arg1) {
            if (arg1.getAction().equals(ACTION_USB_PERMISSION)) {
                boolean granted = arg1.getExtras().getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED);
                if (granted) // User accepted our USB connection. Try to open the device as a serial port
                {
                    Log.d(TAG, "ACTION_USB_PERMISSION granted");
                    Intent intent = new Intent(ACTION_USB_PERMISSION_GRANTED);
                    arg0.sendBroadcast(intent);
                    connection = usbManager.openDevice(device);
                    new ConnectionThread().start();
                } else // User not accepted our USB connection. Send an Intent to the Main Activity
                {
                    Intent intent = new Intent(ACTION_USB_PERMISSION_NOT_GRANTED);
                    arg0.sendBroadcast(intent);
                }
            } else if (arg1.getAction().equals(ACTION_USB_ATTACHED)) {
                Log.d(TAG, "ACTION_USB_ATTACHED");
                if (!serialPortConnected)
                    findSerialPortDevice(); // A USB device has been attached. Try to open it as a Serial port
            } else if (arg1.getAction().equals(ACTION_USB_DETACHED)) {
                Log.d(TAG, "ACTION_USB_DETACHED");
                // Usb device was disconnected. send an intent to the Main Activity
                Intent intent = new Intent(ACTION_USB_DISCONNECTED);
                arg0.sendBroadcast(intent);
                if (serialPortConnected) {
                    serialPort.close();
                }
                serialPortConnected = false;
            }
        }
    };

    /*
     * onCreate will be executed when service is started. It configures an IntentFilter to listen for
     * incoming Intents (USB ATTACHED, USB DETACHED...) and it tries to open a serial port.
     */
    @Override
    public void onCreate() {
        this.context = this;
        serialPortConnected = false;
        UsbService.SERVICE_CONNECTED = true;
        setFilter();
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        findSerialPortDevice();
    }

    /* MUST READ about services
     * http://developer.android.com/guide/components/services.html
     * http://developer.android.com/guide/components/bound-services.html
     */
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return Service.START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        serialPort.close();
        unregisterReceiver(usbReceiver);
        UsbService.SERVICE_CONNECTED = false;
    }

    /*
     * This function will be called from MainActivity to write data through Serial Port
     */
    public void write(byte[] data) {
        Log.d(TAG, "write"+data.toString());
        if (serialPort != null)
            serialPort.write(data);
    }

    public void setHandler(Handler mHandler) {
        this.mHandler = mHandler;
    }

    private void findSerialPortDevice() {
        Log.d(TAG, "findSerialPortDevice");
        // This snippet will try to open the first encountered usb device connected, excluding usb root hubs
        HashMap<String, UsbDevice> usbDevices = usbManager.getDeviceList();
        if (!usbDevices.isEmpty()) {

            Log.d(TAG, "!usbDevices.isEmpty()");

            // first, dump the hashmap for diagnostic purposes
            for (Map.Entry<String, UsbDevice> entry : usbDevices.entrySet()) {
                device = entry.getValue();
                Log.d(TAG, String.format("USBDevice.HashMap (vid:pid) (%X:%X)-%b class:%X:%X name:%s",
                        device.getVendorId(), device.getProductId(),
                        true,//UsbSerialDevice.isSupported(device),
                        device.getDeviceClass(), device.getDeviceSubclass(),
                        device.getDeviceName()));
            }

            for (Map.Entry<String, UsbDevice> entry : usbDevices.entrySet()) {
                device = entry.getValue();
                int deviceVID = device.getVendorId();
                int devicePID = device.getProductId();

//                if (deviceVID != 0x1d6b && (devicePID != 0x0001 && devicePID != 0x0002 && devicePID != 0x0003) && deviceVID != 0x5c6 && devicePID != 0x904c) {
                //if (UsbSerialDevice.isSupported(device)) {
                    // There is a supported device connected - request permission to access it.
                    requestUserPermission();
                    break;
                /*} else {
                    connection = null;
                    device = null;
                }*/
            }
            if (device==null) {
                // There are no USB devices connected (but usb host were listed). Send an intent to MainActivity.
                Intent intent = new Intent(ACTION_NO_USB);
                sendBroadcast(intent);
            }
        } else {
            Log.d(TAG, "findSerialPortDevice() usbManager returned empty device list." );
            // There is no USB devices connected. Send an intent to MainActivity
            Intent intent = new Intent(ACTION_NO_USB);
            sendBroadcast(intent);
        }
    }

    private void setFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(ACTION_USB_DETACHED);
        filter.addAction(ACTION_USB_ATTACHED);
        registerReceiver(usbReceiver, filter);
    }

    /*
     * Request user permission. The response will be received in the BroadcastReceiver
     */
    private void requestUserPermission() {
        Log.d(TAG, String.format("requestUserPermission(%X:%X)", device.getVendorId(), device.getProductId() ) );
        PendingIntent mPendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        usbManager.requestPermission(device, mPendingIntent);
    }

    public class UsbBinder extends Binder {
        public UsbService getService() {
            return UsbService.this;
        }
    }

    /*
     * A simple thread to open a serial port.
     * Although it should be a fast operation. moving usb operations away from UI thread is a good thing.
     */
    private class ConnectionThread extends Thread {
        @Override
        public void run() {
            serialPort = new CP2615SerialDevice(device, connection, 3); //UsbSerialDevice.createUsbSerialDevice(device, connection);//new CP2102SerialDevice(device, connection, -1);//
            if (serialPort != null) {
                if (serialPort.open()) {
                    Log.d(TAG, "serialPort.open()");
                    serialPortConnected = true;
                    serialPort.setBaudRate(BAUD_RATE);
                    serialPort.setDataBits(UsbSerialInterface.DATA_BITS_8);
                    serialPort.setStopBits(UsbSerialInterface.STOP_BITS_1);
                    serialPort.setParity(UsbSerialInterface.PARITY_NONE);
                    /*
                     * Current flow control Options:
                     * UsbSerialInterface.FLOW_CONTROL_OFF
                     * UsbSerialInterface.FLOW_CONTROL_RTS_CTS only for CP2102 and FT232
                     * UsbSerialInterface.FLOW_CONTROL_DSR_DTR only for CP2102 and FT232
                     */
                    serialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
                    //serialPort.read(mCallback);
                    serialPort.getCTS(ctsCallback);
                    serialPort.getDSR(dsrCallback);

                    //
                    // Some Arduinos would need some sleep because firmware wait some time to know whether a new sketch is going
                    // to be uploaded or not
                    //Thread.sleep(2000); // sleep some. YMMV with different chips.

                    // Everything went as expected. Send an intent to MainActivity

                    //ATTEMPT TO SEND COMMAND TO CHANGE LIGHT COLOUR

                    writeGreen();


                    Intent intent = new Intent(ACTION_USB_READY);
                    context.sendBroadcast(intent);
                } else {
                    Log.d(TAG, "serialPort.open() == false");
                    // Serial port could not be opened, maybe an I/O error or if CDC driver was chosen, it does not really fit
                    // Send an Intent to Main Activity
                    Intent intent = new Intent(ACTION_USB_DEVICE_NOT_WORKING);
                    context.sendBroadcast(intent);

                }
            } else {
                Log.d(TAG, "No driver :/");
                // No driver for given device, even generic CDC driver could not be loaded
                Intent intent = new Intent(ACTION_USB_NOT_SUPPORTED);
                context.sendBroadcast(intent);
            }
        }
    }

    //doI2cTransfer(slave, 0, bytes(xfer))

    //slave = 16
    //read = 0
    private void doI2cTransfer(int slave, int read, byte[] data) {

        //1. do write

        //2. do read

    }

    public void writeVolumeCommand(int reg10, int reg1) {
        Log.d(TAG, "writeVolumeCommand()");
        Log.d(TAG, "reg10="+reg10);
        Log.d(TAG, "reg1="+reg1);
        int[] command = new int[]{42 ,42 ,0 ,15 ,212 ,0 ,1 ,136 ,0 ,5 ,136 ,240 ,116 ,reg10 ,reg1 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0, 0};
        Log.d(TAG, "command.length = "+command.length);
        byte[] buffer1 = buildI2CCommand(command);
        int[] blankInts = new int[command.length];
        Arrays.fill(blankInts, 0);
        byte[] buffer2 = buildI2CCommand(blankInts);
        int syncWriteResult1 = connection.bulkTransfer(serialPort.getOutEndpoint(), buffer1, 15, 1500);
        int syncWriteResult2 = connection.bulkTransfer(serialPort.getOutEndpoint(), buffer2, 64, 1500);
        Log.d(TAG, "syncWriteResult1 = "+syncWriteResult1);
        Log.d(TAG, "syncWriteResult2 = "+syncWriteResult2);
    }

    public void writeRed() {

        int[] command = new int[]{
                42 ,
                42 ,
                0 ,
                13 ,
                212 ,
                0 ,
                1 ,
                16 ,
                0 ,
                3 ,1 ,1 ,23 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0};
        byte[] buffer1 = buildI2CCommand(command);

        int[] blankInts = new int[command.length];
        Arrays.fill(blankInts, 0);
        byte[] buffer2 = buildI2CCommand(blankInts);
        int syncWriteResult1 = connection.bulkTransfer(serialPort.getOutEndpoint(), buffer1, 13, 1500);
        int syncWriteResult2 = connection.bulkTransfer(serialPort.getOutEndpoint(), buffer2, 13, 1500);
        Log.d(TAG, "syncWriteResult1 = "+syncWriteResult1);
        Log.d(TAG, "syncWriteResult2 = "+syncWriteResult2);
    }

    public void writeGreen() {
        Log.d(TAG, "writeGreen()");
        int[] command = new int[]{42 ,42 ,0 ,13 ,212 ,0 ,1 ,16 ,0 ,3 ,1 ,2 ,23 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0};
        Log.i(TAG, "Command Hex ="+AltConverter.convertToString(new int[]{42 ,42 ,0 ,13 ,212 ,0 ,1 ,16 ,0 ,3 ,1 ,2 ,23}));
        Log.d(TAG, "command.length = "+command.length);
        byte[] buffer1 = buildI2CCommand(command);
        int[] blankInts = new int[command.length];
        Arrays.fill(blankInts, 0);
        byte[] buffer2 = buildI2CCommand(blankInts);
        int syncWriteResult1 = connection.bulkTransfer(serialPort.getOutEndpoint(), buffer1, 13, 1500);
        int syncWriteResult2 = connection.bulkTransfer(serialPort.getOutEndpoint(), buffer2, 13, 1500);
        Log.d(TAG, "syncWriteResult1 = "+syncWriteResult1);
        Log.d(TAG, "syncWriteResult2 = "+syncWriteResult2);
    }

    public void writeBlue() {

        int[] command = new int[]{42 ,42 ,0 ,13 ,212 ,0 ,1 ,16 ,0 ,3 ,1 ,3 ,23 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0};
        byte[] buffer1 = buildI2CCommand(command);

        int[] blankInts = new int[command.length];
        Arrays.fill(blankInts, 0);
        byte[] buffer2 = buildI2CCommand(blankInts);
        int syncWriteResult1 = connection.bulkTransfer(serialPort.getOutEndpoint(), buffer1, 13, 1500);
        //int syncWriteResult2 = connection.bulkTransfer(serialPort.getOutEndpoint(), buffer2, 13, 1500);
        Log.d(TAG, "syncWriteResult1 = "+syncWriteResult1);
        //Log.d(TAG, "syncWriteResult2 = "+syncWriteResult2);
    }

    public void writeIOPMessage(String[] hexArray) {

        Log.i(TAG, "writeIOPMessage");

        String requestedHex = "";
        int[] command = new int[64];
        for(int k=0; k<hexArray.length; k++) {
            requestedHex+=hexArray[k];
            command[k] = hexToInt(hexArray[k]);
        }

        Log.i(TAG, "requestedHex="+requestedHex);


        Log.i(TAG, "Command Hex ="+AltConverter.convertToString(command));
        int buffer1Length =
        Log.i(TAG, "requestedHex="+requestedHex);

        byte[] buffer1 = buildI2CCommand(command);
        int[] blankInts = new int[command.length]; //64 length
        Arrays.fill(blankInts, 0);
        byte[] buffer2 = buildI2CCommand(blankInts);
        int syncWriteResult1 = connection.bulkTransfer(serialPort.getOutEndpoint(), buffer1, buffer1.length, 1500); //todo check buffer length
        int syncWriteResult2 = connection.bulkTransfer(serialPort.getOutEndpoint(), buffer2, buffer2.length, 1500);
        Log.d(TAG, "syncWriteResult1 = "+syncWriteResult1);
        Log.d(TAG, "syncWriteResult2 = "+syncWriteResult2);

        serialPort.printDeviceStatus();
        serialPort.setupForRead();
        if(!readCallbackSet) {
            readCallbackSet = true;
            serialPort.read(mCallback);
        }
    }


    private byte[] buildI2CCommand(int[] command) {
        byte[] commandBytes = new byte[command.length];

        //Log.i(TAG, "command.length="+command.length);

        for(int k=0; k<commandBytes.length; k++) {
            commandBytes[k]=(byte)(command[k]& 0xFF);
        }

        //Log.i(TAG, "Command="+AltConverter.convertToString(command));


        return commandBytes;
    }

    public static int hexToInt(String hex) {
        return Integer.parseInt(hex, 16);
    }
}