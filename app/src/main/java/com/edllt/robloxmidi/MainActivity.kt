package com.edllt.robloxmidi

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import android.util.Log
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import com.edllt.robloxmidi.databinding.ActivityMainBinding
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.ipc.RootService
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap


var MIDIAIDLConnection: MIDIBackgroundService.AIDLConnection? = null
var MIDIOutputPort: MidiOutputPort? = null
var binding: ActivityMainBinding? = null
val consoleList = AppendCallbackList()

class MIDIBackgroundService : Service() {
    private var this_service: Service? = null

    inner class AIDLConnection(private val isDaemon: Boolean) : ServiceConnection {
        @OptIn(DelicateCoroutinesApi::class)
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            consoleList.add("AIDL onServiceConnected")
            MIDIAIDLConnection = this

            val ipc = IAIDLIPCService.Stub.asInterface(service)

            consoleList.add("Attempting to initialize MIDI service")
            val midiManager = getSystemService(MIDI_SERVICE) as MidiManager
            val devices: Array<MidiDeviceInfo> = midiManager.devices
            var deviceInfo: MidiDeviceInfo? = null

            for (device in devices) {
                if (device.outputPortCount > 0) {
                    deviceInfo = device
                    break
                }
            }

            if (deviceInfo == null) {
                consoleList.add("FAILED TO FIND MIDI DEVICE!")
                this.onServiceDisconnected(name)
                return
            }


            refreshUI()

            val TouchIDPool = object  {
                private val unusedPool = ArrayList<Int>()
                private var nextId = 0

                init {
                    // Initialize the pool with some initial touch IDs
                    for (i in 0..9) {
                        unusedPool.add(nextId)
                        nextId++
                    }
                }

                fun getTouchID(): Int? {
                    if (unusedPool.isEmpty()) {
                        // Pool is exhausted, consider expanding or handling overflow
                        return null
                    }
                    return unusedPool.removeFirst()
                }

                fun releaseTouchID(touchID: Int) {
                    unusedPool.add(touchID)
                }
            }
            var activeTouches = ConcurrentHashMap<Int, Int>()

            //var Y = 2262;
            var Y = 600;

            val robloxKeys = arrayOf<String>("1", "!", "2", "@", "3", "4", "$", "5", "%", "6", "^", "7", "8", "*", "9", "(", "0", "q", "Q", "w", "W", "e", "E", "r", "t", "T", "y", "Y", "u", "i", "I", "o", "O", "p", "P", "a", "s", "S", "d", "D", "f", "g", "G", "h", "H", "j", "J", "k", "l", "L", "z", "Z", "x", "c", "C", "v", "V", "b", "B", "n", "m")
            val coordinates = arrayOf<Int>(140, 180, 210, 240, 270,320,360,380,420,450,480,510,560,600,630,660,700,740,780,800,840,880,900,930,990,1000,1040,1080,1120,1160,1200,1220,1260,1290,1320,1360,1400,1430,1470,1500,1540,1580,1610,1650,1690,1710,1740,1780,1820,1860,1880,1920,1960,2000,2040,2060,2100,2130,2160,2200,2250)
            /* (CHARGEPORT LEFT)
                    TRUE Y 2262
                    Relative X 140
                    OFFSET 2122
                    FORMULA: 2,262 - (NEWVALUE - 140)
            */
            //var relativeCoordinateX = coordinates[index - 36]
            //var trueCoordinate = 2262 - (relativeCoordinateX - coordinates[0])

            midiManager.openDevice(
                deviceInfo,
                { device ->
                    if (device == null) {
                        consoleList.add("Failed to open device " + deviceInfo);
                        this.onServiceDisconnected(name)
                    } else {
                        consoleList.add("Connected to $device")

                        class MyReceiver : MidiReceiver() {
                            private val NOTE_ON = 0x90
                            private val NOTE_OFF = 0x80
                            private val ALIVE: Byte = 0xFE.toByte()

                            private fun logByteArray(prefix: String, data: ByteArray, offset: Int, count: Int) {
                                val builder = StringBuilder(prefix)
                                for (i in 0 until count) {
                                    builder.append(String.format("0x%02X", data[offset + i]))
                                    if (i != count - 1) {
                                        builder.append(", ")
                                    }
                                }
                                consoleList.add(builder.toString())
                            }

                            @Throws(IOException::class)
                            override fun onSend(
                                data: ByteArray, offset: Int,
                                count: Int, timestamp: Long
                            ) {
                                // Ignore the alive signal
                                if (data[offset] == ALIVE) {
                                    return
                                }

                                for (i in offset until offset + count) {
                                    val byte = data[i].toInt() and 0xFF
                                    if (byte >= 0x80) { // Status byte
                                        val messageType = byte and 0xF0
                                        val channel = byte and 0x0F + 1
                                        val noteNumber = data[i + 1].toInt()

                                        var isDown: String = ""
                                        if (messageType == NOTE_ON) {
                                            isDown = "Down"
                                        } else if (messageType == NOTE_OFF) {
                                            isDown = "Up"
                                            continue
                                        } else {
                                            continue
                                        }

                                        try {
                                            if (noteNumber >= 36 && noteNumber <= 96) {
                                                var touch_id: Int? = null
                                                if (isDown == "Down" && !activeTouches.containsKey(noteNumber)) {
                                                    touch_id = TouchIDPool.getTouchID()

                                                    if (touch_id != null) {
                                                        activeTouches[noteNumber] = touch_id
                                                    }
                                                }
//                                                else if (isDown == "Up") {
//                                                    if (activeTouches.containsKey(noteNumber)) {
//                                                        touch_id = activeTouches[noteNumber]
//
//                                                        if (touch_id != null) {
//                                                            activeTouches.remove(noteNumber)
//                                                            TouchIDPool.releaseTouchID(touch_id)
//                                                        }
//                                                        // Prevents the up key from communicating with ipc
//                                                        continue
//                                                    }
//                                                }


                                                if (touch_id != null) {
                                                    consoleList.add(ipc.pianoKey(550, coordinates[noteNumber - 36], touch_id, isDown))
                                                    activeTouches.remove(noteNumber)
                                                    TouchIDPool.releaseTouchID(touch_id)
//                                                    consoleList.add("Key pressed: IsDown $isDown, Note $noteNumber")
                                                } else {
                                                    val errMsg ="No free touch id found. touch_id is null"

                                                    Toast.makeText(this_service, errMsg, Toast.LENGTH_LONG).show()
                                                    consoleList.add(errMsg)
                                                }
                                            }
                                        } catch (exception: Exception) {
                                            val errMsg = "ERROR ISDOWN $isDown: $exception"

                                            Toast.makeText(this_service, errMsg, Toast.LENGTH_LONG).show()
                                            consoleList.add(errMsg)
                                        }
                                    }
                                }
                            }
                        }

                        MIDIOutputPort?.close()
                        MIDIOutputPort = device.openOutputPort(0)
                        MIDIOutputPort?.connect(MyReceiver())

                    }
                },
                Handler(Looper.getMainLooper())
            )

        }

        override fun onServiceDisconnected(name: ComponentName) {
            consoleList.add("AIDL onServiceDisconnected")


            if (this_service != null) {
                stopService(Intent(this_service, MIDIBackgroundService::class.java))
            } else {
                MIDIOutputPort?.close()
                if (MIDIAIDLConnection != null) {
                    RootService.unbind(MIDIAIDLConnection!!)
                    MIDIAIDLConnection=null
                }
                refreshUI()
                consoleList.add("this_service is null, attempted to manually close MIDIOutputPort and MIDIAIDLConnection")
            }

            refreshUI()
        }
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channelId = createNotificationChannel("MIDI_Service", "MIDI Background Service")

        val notification: Notification = Notification.Builder(this, channelId)
            .setContentTitle("title")
            .setContentText("text")
            .build()
        startForeground(2001, notification)

        return START_STICKY
    }

    private fun createNotificationChannel(channelId: String, channelName: String): String{
        val chan = NotificationChannel(channelId,
            channelName, NotificationManager.IMPORTANCE_HIGH)
        chan.lightColor = Color.BLUE
        chan.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(chan)
        return channelId
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        this_service = this
        val intent = Intent(this, AIDLService::class.java)
        intent.addCategory(RootService.CATEGORY_DAEMON_MODE)
        RootService.bind(intent, AIDLConnection(true))
    }

    override fun onDestroy() {
        // clean up your service logic here
        MIDIOutputPort?.close()
        if (MIDIAIDLConnection != null) {
            RootService.unbind(MIDIAIDLConnection!!)
            MIDIAIDLConnection = null
        }
        refreshUI()
        consoleList.add("Closing service, MIDI outputport closed and rootservice unbinded")
    }

}

private fun refreshUI() {
    binding?.connectBtn?.text = if (MIDIAIDLConnection == null) "Connect MIDI" else "Disconnect MIDI"
}

class AppendCallbackList : CallbackList<String?>() {
    override fun onAddElement(s: String?) {
        Log.d(MainActivity.TAG, "CONSOLE: $s")
        binding?.console?.append(s)
        binding?.console?.append("\n")
        binding?.sv?.postDelayed({ binding?.sv?.fullScroll(ScrollView.FOCUS_DOWN) }, 10)
    }
}

class MainActivity : AppCompatActivity() {
//    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding!!.root)

        binding!!.connectBtn.setOnClickListener { _: View? ->
            if (MIDIAIDLConnection == null) {
//                val intent = Intent(this, AIDLService::class.java)
//                intent.addCategory(RootService.CATEGORY_DAEMON_MODE)
//                RootService.bind(intent, AIDLConnection(true)
                startService(Intent(this, MIDIBackgroundService::class.java))

            } else {
//                RootService.unbind(MIDIAIDLConnection!!)
                stopService(Intent(this, MIDIBackgroundService::class.java))
            }
        }

        binding!!.killBtn.setOnClickListener { _: View? ->
            try {
                stopService(Intent(this, MIDIBackgroundService::class.java))
                consoleList.add("Successfully stopped background service")
            } catch(_: Exception) {
                consoleList.add("No background service to stop")
            }

            if (MIDIAIDLConnection != null) {
                RootService.unbind(MIDIAIDLConnection!!)

                MIDIAIDLConnection = null
            }
            MIDIOutputPort?.close()
        }

        binding!!.clearBtn.setOnClickListener { _: View? ->
            binding!!.console.text=""
        }

        // TEMP
        consoleList.add("Welcome to Roblox MIDI v2.1!")
        consoleList.add("Made by EDllT")
        refreshUI()
//        binding!!.console.text = "Welcome"
    }

    override fun onDestroy() {
        super.onDestroy()

        binding=null
    }

    /**
     * A native method that is implemented by the 'robloxmidi' native library,
     * which is packaged with this application.
     */
//    external fun stringFromJNI(): String

    companion object {
        // Used to load the 'robloxmidi' library on application startup.
        const val TAG = "Roblox_MIDI"

        init {
//            System.loadLibrary("robloxmidi")
        }
    }
}