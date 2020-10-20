package com.hearxgroup.dactest

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

import android.widget.Toast
import android.content.*
import android.os.Handler
import android.widget.TextView
import android.os.IBinder
import android.os.Message
import android.text.method.ScrollingMovementMethod
import com.hearxgroup.dactest.UsbService.hexToInt
import kotlinx.android.synthetic.main.activity_dac_v3_raw.*
import java.lang.ref.WeakReference

class DACV3Activity : AppCompatActivity() {

    /*
     * Notifications from UsbService will be received here.
     */
    private val mUsbReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbService.ACTION_USB_PERMISSION_GRANTED // USB PERMISSION GRANTED
                -> Toast.makeText(context, "USB Ready", Toast.LENGTH_SHORT).show()
                UsbService.ACTION_USB_PERMISSION_NOT_GRANTED // USB PERMISSION NOT GRANTED
                -> Toast.makeText(context, "USB Permission not granted", Toast.LENGTH_SHORT).show()
                UsbService.ACTION_NO_USB // NO USB CONNECTED
                -> Toast.makeText(context, "No USB connected", Toast.LENGTH_SHORT).show()
                UsbService.ACTION_USB_DISCONNECTED // USB DISCONNECTED
                -> Toast.makeText(context, "USB disconnected", Toast.LENGTH_SHORT).show()
                UsbService.ACTION_USB_NOT_SUPPORTED // USB NOT SUPPORTED
                -> Toast.makeText(context, "USB device not supported", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private var usbService: UsbService? = null
    private var serial_output: TextView? = null
    private var mHandler: MyHandler? = null
    private val usbConnection = object : ServiceConnection {
        override fun onServiceConnected(arg0: ComponentName, arg1: IBinder) {
            usbService = (arg1 as UsbService.UsbBinder).service
            usbService!!.setHandler(mHandler)
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            usbService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dac_v3_raw)
        mHandler = MyHandler(this)
        tv_usb_output.movementMethod= ScrollingMovementMethod()
        this.serial_output = tv_usb_output

        btn_clear.setOnClickListener {
            tv_usb_output.text = ""
            edt_id_msb.setText("")
            edt_id_lsb.setText("")
            edt_payload_0.setText("")
            edt_payload_1.setText("")
            edt_payload_2.setText("")
            edt_payload_3.setText("")
            edt_payload_4.setText("")
            edt_payload_5.setText("")
            edt_payload_6.setText("")
        }

        btn_send.setOnClickListener {
            serial_output!!.text = ""
            sendSerialCommand()
        }
    }

    public override fun onResume() {
        super.onResume()
        setFilters()  // Start listening notifications from UsbService
        startService(
            UsbService::class.java,
            usbConnection,
            null
        ) // Start UsbService(if it was not started before) and Bind it
    }

    public override fun onPause() {
        super.onPause()
        unregisterReceiver(mUsbReceiver)
        unbindService(usbConnection)
    }

    private fun startService(
        service: Class<*>,
        serviceConnection: ServiceConnection,
        extras: Bundle?
    ) {
        if (!UsbService.SERVICE_CONNECTED) {
            val startService = Intent(this, service)
            if (extras != null && !extras.isEmpty) {
                val keys = extras.keySet()
                for (key in keys) {
                    val extra = extras.getString(key)
                    startService.putExtra(key, extra)
                }
            }
            startService(startService)
        }
        val bindingIntent = Intent(this, service)
        bindService(bindingIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun setFilters() {
        val filter = IntentFilter()
        filter.addAction(UsbService.ACTION_USB_PERMISSION_GRANTED)
        filter.addAction(UsbService.ACTION_NO_USB)
        filter.addAction(UsbService.ACTION_USB_DISCONNECTED)
        filter.addAction(UsbService.ACTION_USB_NOT_SUPPORTED)
        filter.addAction(UsbService.ACTION_USB_PERMISSION_NOT_GRANTED)
        registerReceiver(mUsbReceiver, filter)
    }

    /*
     * This handler will be passed to UsbService. Data received from serial port is displayed through this handler
     */
    private class MyHandler(activity: DACV3Activity) : Handler() {
        private val mActivity: WeakReference<DACV3Activity> = WeakReference(activity)

        override fun handleMessage(msg: Message) {
            when (msg.what) {
                UsbService.MESSAGE_FROM_SERIAL_PORT -> {
                    var data = msg.obj as String
                    var dataList = data.chunked(8).toMutableList()
                    for(k in 0 until dataList.size) {
                        when(k) {
                            0 -> dataList[k]+=" --Preamble MSB"
                            1 -> dataList[k]+=" --Preamble LSB"
                            2 -> dataList[k]+=" --Length MSB"
                            3 -> dataList[k]+=" --Length LSB"
                            4 -> dataList[k]+=" --Message ID MSB"
                            5 -> dataList[k]+=" --Message ID LSB \n\nPAYLOAD"
                            else -> dataList[k]+=" --Payload[${k-6}]"
                        }

                    }
                    mActivity.get()?.serial_output?.append(dataList.joinToString { "\n" +  it })
                }
                UsbService.CTS_CHANGE -> Toast.makeText(
                    mActivity.get(),
                    "CTS_CHANGE",
                    Toast.LENGTH_LONG
                ).show()
                UsbService.DSR_CHANGE -> Toast.makeText(
                    mActivity.get(),
                    "DSR_CHANGE",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun sendSerialCommand() {

        var messageIdMSB = edt_id_msb.text.toString().trim()
        var messageIdLSB = edt_id_lsb.text.toString().trim()

        if(messageIdMSB.isNullOrEmpty())
            messageIdMSB = "0"

        if(messageIdLSB.isNullOrEmpty())
            messageIdLSB = "0"

        val payloadList = mutableListOf<String>()

        if(edt_payload_0.text.toString().isNotEmpty())
            payloadList.add(edt_payload_0.text.toString())

        if(edt_payload_1.text.toString().isNotEmpty())
            payloadList.add(edt_payload_1.text.toString())

        if(edt_payload_2.text.toString().isNotEmpty())
            payloadList.add(edt_payload_2.text.toString())

        if(edt_payload_3.text.toString().isNotEmpty())
            payloadList.add(edt_payload_3.text.toString())

        if(edt_payload_4.text.toString().isNotEmpty())
            payloadList.add(edt_payload_4.text.toString())

        if(edt_payload_5.text.toString().isNotEmpty())
            payloadList.add(edt_payload_5.text.toString())

        if(edt_payload_6.text.toString().isNotEmpty())
            payloadList.add(edt_payload_6.text.toString())

        val messageList = mutableListOf(
            "2A", //Preamble MSB
            "2A", //Preamble LSB
            "0", //Length MSB
            (6 + payloadList.size + 1).toString(16), //Length LSB Total message length (header + message payload).
            messageIdMSB, //Message ID MSB
            messageIdLSB //Message ID LSB
        )

        messageList.addAll(payloadList)

        messageList.add(
            //Payload Size
            payloadList.map { hexToInt(it) }.sum().toString(16)
        )

        usbService?.writeIOPMessage(
            //IOP MESSAGE
            messageList.toTypedArray()
        )
    }

}