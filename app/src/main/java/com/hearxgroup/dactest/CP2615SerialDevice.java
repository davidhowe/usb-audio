package com.hearxgroup.dactest;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbRequest;
import android.util.Log;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.utils.SafeUsbRequest;
import com.google.gson.Gson;

public class CP2615SerialDevice extends UsbSerialDevice
{

    private static final String TAG = CP2615SerialDevice.class.getSimpleName();
    private static final String CLASS_ID = CP2615SerialDevice.class.getSimpleName();

    private boolean readSetup = false;

    private final UsbInterface mInterface;
    private final UsbInterface zeroInterface;
    private UsbEndpoint inEndpoint;
    private UsbEndpoint outEndpoint;

    public CP2615SerialDevice(UsbDevice device, UsbDeviceConnection connection, int iface)
    {
        super(device, connection);

        Log.d(TAG, "Interface count ="+device.getInterfaceCount());

        for(int h=0; h<device.getInterfaceCount(); h++) {
            Log.d(TAG, "\nInterface "+h+"="+device.getInterface(h));

            for(int k=0; k< device.getInterface(h).getEndpointCount(); k++) {
                UsbEndpoint endpoint = device.getInterface(h).getEndpoint(k);
                Log.d(TAG, "endpoint "+k+" type = "+endpoint.getType());
                Log.d(TAG, "endpoint "+k+" dir = "+endpoint.getDirection());
                Log.d(TAG, "endpoint "+k+" add = "+endpoint.getAddress());
            }
        }

        mInterface = device.getInterface(iface >= 0 ? iface : 0);
        zeroInterface = device.getInterface(0);

        Log.d(TAG, "Chosen Interface="+mInterface);
        Log.d(TAG, "zeroInterface Interface="+zeroInterface);
    }

    public UsbEndpoint getOutEndpoint() {
        return outEndpoint;
    }

    public UsbEndpoint getInEndpoint() {
        return inEndpoint;
    }

    public void setupForRead() {
        Log.d(TAG, "setupForRead()");
        if(!readSetup) {
            readSetup = true;
            UsbRequest requestIN = new SafeUsbRequest();
            requestIN.initialize(connection, inEndpoint);
            this.workerThread.setUsbRequest(requestIN);
        }
    }

    public void printDeviceStatus() {
        Log.d(TAG, "printDeviceStatus()");
        Log.d(TAG, "asyncMode="+asyncMode);
        if(readThread!=null) {
            Log.d(TAG, "readThread not null");
        } else {
            Log.d(TAG, "readThread is null");
        }

        if(workerThread!=null) {
            Log.d(TAG, "workerThread not null");
            if(workerThread.getUsbRequest()!=null)
                Log.d(TAG, "workerThread getUsbRequest not null");
            else
                Log.d(TAG, "workerThread getUsbRequest is null");
        } else {
            Log.d(TAG, "workerThread is null");
        }

        if(getOutEndpoint()!=null)
            Log.d(TAG, "outEndpoint not null");
        else
            Log.d(TAG, "outEndpoint is null");

        if(getInEndpoint()!=null)
            Log.d(TAG, "inEndpoint not null");
        else
            Log.d(TAG, "inEndpoint is null");

        if(serialBuffer!=null) {
            Log.d(TAG, "serialBuffer not null");
            if(serialBuffer.getReadBuffer()!=null)
                Log.d(TAG, "serialBuffer readbuffer not null");
            else
                Log.d(TAG, "serialBuffer readbuffer is null");
        }
        else
            Log.d(TAG, "serialBuffer is null");
    }

    @Override
    public boolean open()
    {
        Log.d(TAG, "open()");

        boolean ret = openCP2615IOLink();

        Log.d(TAG, "ret="+ret);

        if(ret) {
            // Initialize UsbRequest
            UsbRequest requestIN = new SafeUsbRequest();
            requestIN.initialize(connection, inEndpoint);
            restartWorkingThread();
            asyncMode = true;
            isOpen = true;

            return true;
        }else
        {
            isOpen = false;
            return false;
        }
    }

    @Override
    public void close()
    {
        Log.d(TAG, "close()");
        killWorkingThread();
        killWriteThread();
        connection.releaseInterface(mInterface);
        isOpen = false;
    }

    @Override
    public boolean syncOpen() {
        Log.d(TAG, "syncOpen()");
        boolean ret = openCP2615IOLink();
        if(ret) {
            asyncMode = true;
            isOpen = true;
            return true;
        }else
        {
            isOpen = false;
            return false;
        }
    }

    @Override
    public void syncClose()
    {
        Log.d(TAG, "syncOpen()");
        //setControlCommand(CP210x_PURGE, CP210x_PURGE_ALL, null);
        //setControlCommand(CP210x_IFC_ENABLE, CP210x_UART_DISABLE, null);
        connection.releaseInterface(mInterface);
        isOpen = false;
    }

    @Override
    public void setBaudRate(int baudRate) {
    }

    @Override
    public void setDataBits(int dataBits) {

    }

    @Override
    public void setStopBits(int stopBits) {

    }

    @Override
    public void setParity(int parity) {

    }

    @Override
    public void setFlowControl(int flowControl) {

    }

    @Override
    public void setBreak(boolean state) {

    }

    @Override
    public void setRTS(boolean state) {

    }

    @Override
    public void setDTR(boolean state) {

    }

    @Override
    public void getCTS(UsbCTSCallback ctsCallback)
    {
    }

    @Override
    public void getDSR(UsbDSRCallback dsrCallback)
    {
    }

    @Override
    public void getBreak(UsbBreakCallback breakCallback)
    {
    }

    @Override
    public void getFrame(UsbFrameCallback frameCallback)
    {
    }

    @Override
    public void getOverrun(UsbOverrunCallback overrunCallback)
    {
    }

    @Override
    public void getParity(UsbParityCallback parityCallback)
    {
    }

    private boolean openCP2615IOLink()
    {
        Log.d(TAG, "openCP2615IOLink()");

        if(connection.claimInterface(mInterface, true))
        {
            connection.setInterface(mInterface);
            Log.i(CLASS_ID, "mInterface succesfully claimed");
            Log.i(CLASS_ID, "mInterface = "+mInterface.toString());
        }else
        {
            Log.i(CLASS_ID, "mInterface could not be claimed");
            return false;
        }

        // Assign endpoints
        int numberEndpoints = mInterface.getEndpointCount();
        Log.i(CLASS_ID, "numberEndpoints="+numberEndpoints);
        for(int i=0;i<=numberEndpoints-1;i++) {
            int altSetting = mInterface.getAlternateSetting();
            Log.d(CLASS_ID, "mInterface altSetting="+altSetting);

            UsbEndpoint endpoint = mInterface.getEndpoint(i);

            Log.d(CLASS_ID, "endpoint="+ new Gson().toJson(endpoint));

            if(endpoint.getDirection()==UsbConstants.USB_DIR_IN) {
                inEndpoint = endpoint;
                Log.i(CLASS_ID, "inEndpoint add = "+inEndpoint.getAddress());
                Log.i(CLASS_ID, "inEndpoint type = "+inEndpoint.getType());
                Log.i(CLASS_ID, "inEndpoint dir = "+inEndpoint.getDirection());
                Log.i(CLASS_ID, "inEndpoint attrs = "+inEndpoint.getAttributes());
            } else if(endpoint.getDirection()==UsbConstants.USB_DIR_OUT) {
                outEndpoint = endpoint;
                Log.i(CLASS_ID, "outEndpoint type = "+outEndpoint.getType());
                Log.i(CLASS_ID, "outEndpoint add = "+outEndpoint.getAddress());
                Log.i(CLASS_ID, "outEndpoint dir = "+outEndpoint.getDirection());
                Log.i(CLASS_ID, "outEndpoint attrs = "+outEndpoint.getAttributes());
            }

        /*    UsbConstants.USB_ENDPOINT_XFER_BULK
            UsbConstants.USB_DIR_IN*/

            /*if(endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK
                    && endpoint.getDirection() == UsbConstants.USB_DIR_IN)
            {
                inEndpoint = endpoint;
                Log.i(CLASS_ID, "inEndpoint add = "+inEndpoint.getAddress());
                Log.i(CLASS_ID, "inEndpoint type = "+inEndpoint.getType());
            }else
            {

                outEndpoint = endpoint;
                Log.i(CLASS_ID, "outEndpoint type = "+outEndpoint.getType());
                Log.i(CLASS_ID, "outEndpoint add = "+outEndpoint.getAddress());
            }*/
        }

        // Default Setup

       /*boolean zeroInterfaceClaimed = connection.claimInterface(zeroInterface, true);
       Log.d(TAG, "zeroInterfaceClaimed="+zeroInterfaceClaimed);

        Log.i(CLASS_ID, "zeroInterface altSetting="+zeroInterface.getAlternateSetting());

       if(setControlCommand(0x06, 0x200, null) < 0)
            return true;
        else
            return true;*/

       return true;
    }

    private int setControlCommand(int request, int value, byte[] data)
    {
        int dataLength = 0;
        if(data != null)
        {
            dataLength = data.length;
        }
        int response = -1;//connection.controlTransfer(CP210x_REQTYPE_HOST2DEVICE, request, value, zeroInterface.getId(), data, dataLength, USB_TIMEOUT);
        Log.i(CLASS_ID,"Control Transfer Response: " + String.valueOf(response));
        return response;
    }
}