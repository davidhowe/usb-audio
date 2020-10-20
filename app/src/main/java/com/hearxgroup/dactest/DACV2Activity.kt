package com.hearxgroup.dactest

import androidx.appcompat.app.AppCompatActivity

import android.widget.Toast
import android.content.*
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.*
import android.util.Log
import android.widget.ArrayAdapter
import kotlinx.android.synthetic.main.activity_dac_v2.*
import java.io.File
import java.lang.ref.WeakReference

class DACV2Activity : AppCompatActivity() {

    private var soundPool: SoundPool? = null
    private lateinit var audioManager: AudioManager

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

    private var attenuation = 0
    private var reg10 = 224
    private var reg1 = 208
    private var soundId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dac_v2)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        soundPool = SoundPool.Builder()
            .setAudioAttributes(attributes)
            .setMaxStreams(10)
            .build()

        soundPool!!.setOnLoadCompleteListener { soundPool, sampleId, status ->
            Log.d("", "sampleId=$sampleId")
            Log.d("", "soundId=$soundId")

            if(soundId>0) {
                soundPool!!.play(
                    soundId,
                    1.0f,
                    1.0f,
                    1,
                    if(rbtn_loop.isChecked) -1 else 0,
                    1.0f)
            }
        }

        mHandler = MyHandler(this)

        tv_current_attenuation.text = "Current Attenuation: 0dB"

        btn_minus_5.setOnClickListener {
            if(attenuation<75) {
                attenuation+=5
                configDAC()
            } else {
                Toast.makeText(this, "Cannot proceed", Toast.LENGTH_LONG).show()
            }
        }

        btn_minus_1.setOnClickListener {
            if(attenuation<79) {
                attenuation+=1
                configDAC()
            } else {
                Toast.makeText(this, "Cannot proceed", Toast.LENGTH_LONG).show()
            }
        }

        btn_plus_1.setOnClickListener {
            if(attenuation>0) {
                attenuation-=1
                configDAC()
            } else {
                Toast.makeText(this, "Cannot proceed", Toast.LENGTH_LONG).show()
            }
        }

        btn_plus_5.setOnClickListener {
            if(attenuation>4) {
                attenuation-=5
                configDAC()
            } else {
                Toast.makeText(this, "Cannot proceed", Toast.LENGTH_LONG).show()
            }
        }

        btn_play.setOnClickListener {
            soundId = loadFileForPlayback(resources.getStringArray(R.array.array_freqs)[spin_freq.selectedItemPosition].toInt(), rbtn_low.isChecked)
        }

        btn_pause.setOnClickListener {
            if(soundId>0)
                soundPool!!.stop(soundId)
        }

        //SETUP FREQ SPINNER
        val freqAdapter = ArrayAdapter<String>(
            this,
            android.R.layout.simple_dropdown_item_1line,
            resources.getStringArray(R.array.array_freqs)
        )
        freqAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spin_freq.adapter = freqAdapter

        rbtn_pulse.isChecked = true
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

    private fun configDAC() {
        adjustRegistersForNewAttenuation(attenuation)
        tv_current_attenuation.text = "Current Attenuation: ${attenuation}dB"
        if (usbService != null) { // if UsbService was correctly binded, Send data
            usbService!!.writeVolumeCommand(reg10, reg1)
        }
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
    private class MyHandler(activity: DACV2Activity) : Handler() {
        private val mActivity: WeakReference<DACV2Activity> = WeakReference(activity)

        override fun handleMessage(msg: Message) {
            when (msg.what) {
                UsbService.MESSAGE_FROM_SERIAL_PORT -> {
                    val data = msg.obj as String
                    mActivity.get()?.tv_usb_output_sample?.append(data)
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

    private fun adjustRegistersForNewAttenuation(attenuation: Int) {
        if(attenuation<10) {
            reg10 = 224
            reg1 = 208 + attenuation
        }
        else if(attenuation<20) {
            reg10 = 225
            reg1 = 208 + (attenuation-10)
        }
        else if(attenuation<30) {
            reg10 = 226
            reg1 = 208 + (attenuation-20)
        }
        else if(attenuation<40) {
            reg10 = 227
            reg1 = 208 + (attenuation-30)
        }
        else if(attenuation<50) {
            reg10 = 228
            reg1 = 208 + (attenuation-40)
        }
        else if(attenuation<60) {
            reg10 = 229
            reg1 = 208 + (attenuation-50)
        }
        else if(attenuation<70) {
            reg10 = 230
            reg1 = 208 + (attenuation-60)
        }
        else if(attenuation<80) {
            reg10 = 231
            reg1 = 208 + (attenuation-70)
        }
    }


    private fun loadFileForPlayback(freq: Int, low: Boolean) : Int {
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
            0
        )

        val filePath = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DCIM).toString()+ File.separator+"dacfiles"+File.separator+"f${freq}_${if(low)"low" else "high"}.wav"
        Log.d("","filePath=$filePath")
        return soundPool!!.load(filePath, 1)
    }

}
