package com.sleipold.dreamstream

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Point
import android.media.AudioManager
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.*
import android.widget.*
import androidmads.library.qrgenearator.QRGContents
import androidmads.library.qrgenearator.QRGEncoder
import androidx.annotation.WorkerThread
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import com.google.android.gms.nearby.connection.*
import com.google.android.gms.vision.CameraSource
import com.google.android.gms.vision.Detector
import com.google.android.gms.vision.barcode.Barcode
import com.google.android.gms.vision.barcode.BarcodeDetector
import com.google.zxing.WriterException
import kotlinx.android.synthetic.main.activity_connection.*
import java.io.IOException

class Connection : AConnection() {

    /* member */
    lateinit var mContext: Context
    override lateinit var mName: String
    override val mServiceId: String = "com.sleipold.dreamstream"
    override val mStrategy: Strategy = Strategy.P2P_POINT_TO_POINT
    private var mState = State.UNKNOWN
    private var mRecorder: AudioRecorder? = null
    private var mAudioPlayer: AudioPlayer? = null
    private var mOriginalVolume: Int = 0
    private var mQrCodeDetector: BarcodeDetector? = null
    private var mCameraSource: CameraSource? = null
    private var mQrCodeValue = ""

    /* components */
    private lateinit var cCurrentState: TextView
    private lateinit var cRoleName: TextView
    private lateinit var cAudioRecordThreshold: SeekBar
    private lateinit var cQrCode: ImageView
    private lateinit var cCamera: SurfaceView
    private lateinit var cQrCodeValue: TextView
    private lateinit var cConnect: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connection)

        /* member */
        mContext = applicationContext
        mName = intent.getStringExtra("role")!!

        /* components */
        cCurrentState = findViewById(R.id.txtCurrState)
        cRoleName = findViewById(R.id.txtRoleName)
        cAudioRecordThreshold = findViewById(R.id.sbAudioRecordThreshold)
        cQrCode = findViewById(R.id.ivQrImage)
        cCamera = findViewById(R.id.svScreen)
        cQrCodeValue = findViewById(R.id.txtQrValue)
        cConnect = findViewById(R.id.btnConnect)

        cRoleName.text = mName

        cAudioRecordThreshold.isVisible = false
        cQrCode.isVisible = false

        cConnect.setOnClickListener {
            if (mQrCodeValue.isNotEmpty()) {
                cCamera.isVisible = false
                cQrCodeValue.isVisible = false
                cConnect.isVisible = false
                setState(State.SEARCHING)
            }
        }

        if (mName == "receiver") {
            // receiver device: only QR-Code is visible until device is connected to sender
            cQrCode.isVisible = true
            cCamera.isVisible = false
            cQrCodeValue.isVisible = false
            cConnect.isVisible = false

            cAudioRecordThreshold.setOnSeekBarChangeListener(object :
                SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar,
                    progress: Int,
                    fromUser: Boolean
                ) {
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    // send audio record threshold from receiver to sender
                    val threshold = seekBar.progress.toString()
                    val payload = Payload.fromBytes(threshold.toByteArray())
                    send(payload)
                }
            })
        }
    }

    override fun onStart() {
        super.onStart()

        // Set the media volume to max .
        volumeControlStream = AudioManager.STREAM_MUSIC
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        mOriginalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0
        )

        setState(State.AVAILABLE)
    }

    override fun onStop() {
        // Restore the original volume.
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, mOriginalVolume, 0)
        volumeControlStream = AudioManager.USE_DEFAULT_STREAM_TYPE

        // Stop all audio-related threads
        if (isRecording()) {
            stopRecording()
        }
        if (isPlaying()) {
            stopPlaying()
        }

        // After our Activity stops, we disconnect from Nearby Connections.
        setState(State.UNKNOWN)

        super.onStop()
    }

    override fun onReceive(endpoint: Endpoint?, payload: Payload) {
        when (payload.type) {
            Payload.Type.STREAM -> {
                if (mAudioPlayer != null) {
                    mAudioPlayer!!.stop()
                    mAudioPlayer = null
                }

                val player = object : AudioPlayer(payload.asStream()!!.asInputStream(), mContext) {
                    @WorkerThread
                    override fun onFinish() {
                        runOnUiThread { mAudioPlayer = null }
                    }
                }
                mAudioPlayer = player
                player.start()
            }

            Payload.Type.BYTES -> {
                if (mRecorder != null) {
                    // sending threshold from receiver to sender
                    mRecorder!!.mAudioRecordThreshold = String(payload.asBytes()!!).toInt()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        mCameraSource!!.release()
    }

    override fun onResume() {
        super.onResume()
        initialiseDetectorsAndSources()
    }


    private fun setState(pState: State) {
        if (mState == pState) {
            println("State set to $pState but device was already in this state.")
            return
        }

        mState = pState
        println("State set to $pState")
        onStateChanged(pState)
    }

    /**
     * State has changed.
     *
     * @param newState The new state. Prepare device for this state.
     */
    private fun onStateChanged(newState: State) {
        // update nearby connections to the new state
        when (newState) {
            State.SEARCHING -> {
                cCurrentState.setText(R.string.state_searching)
                disconnectFromAllEndpoints()
                startDiscovering()
                startAdvertising()
            }
            State.AVAILABLE -> {
                cCurrentState.setText(R.string.state_available)
                disconnectFromAllEndpoints()
                if (mName == "receiver") {
                    generateQrCode()
                }
                if (mName == "sender") {
                    readQrCode()
                }
            }
            State.CONNECTED -> {
                cCurrentState.setText(R.string.state_connected)
                stopDiscovering()
                stopAdvertising()
                if (mName == "receiver") {
                    cAudioRecordThreshold.isVisible = true
                    cQrCode.isVisible = false
                }
                if (mName == "sender") {
                    startRecording()
                }
            }
            State.UNKNOWN -> {
                stopAllEndpoints()
                cCurrentState.setText(R.string.state_unknown)
            }
        }
    }

    private fun readQrCode() {
        initialiseDetectorsAndSources()
    }

    private fun generateQrCode() {
        if (mServiceId.isNotEmpty()) {
            val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            val display = windowManager.defaultDisplay
            val point = Point()
            display.getSize(point)
            val width = point.x / 2
            val height = point.y / 2
            var smallerDimension = if (width < height) width else height
            smallerDimension = smallerDimension * 3 / 4

            var qrgEncoder = QRGEncoder(
                mServiceId, null,
                QRGContents.Type.TEXT,
                smallerDimension
            )
            try {
                var bitmap = qrgEncoder.encodeAsBitmap()
                cQrCode.setImageBitmap(bitmap)
                setState(State.SEARCHING)
            } catch (e: WriterException) {
                println(e.toString())
            }
        } else {
            println("mServiceId must not be empty")
        }
    }

    private fun initialiseDetectorsAndSources() {

        Toast.makeText(
            applicationContext,
            getString(R.string.qr_scanner_started),
            Toast.LENGTH_SHORT
        ).show()

        mQrCodeDetector = BarcodeDetector.Builder(this)
            .setBarcodeFormats(Barcode.QR_CODE)
            .build()

        mCameraSource = CameraSource.Builder(this, mQrCodeDetector!!)
            .setRequestedPreviewSize(1920, 1080)
            .setAutoFocusEnabled(true)
            .build()

        cCamera.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                try {
                    if (ActivityCompat.checkSelfPermission(
                            this@Connection,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        mCameraSource!!.start(cCamera.holder)
                    } else {
                        ActivityCompat.requestPermissions(
                            this@Connection,
                            arrayOf(Manifest.permission.CAMERA),
                            REQUEST_CAMERA_PERMISSION
                        )
                    }

                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {
            }


            override fun surfaceDestroyed(holder: SurfaceHolder) {
                mCameraSource!!.stop()
            }
        })

        mQrCodeDetector!!.setProcessor(object : Detector.Processor<Barcode> {
            override fun release() {
                Toast.makeText(
                    applicationContext,
                    getString(R.string.qr_code_prevent_memory_leak),
                    Toast.LENGTH_SHORT
                ).show()
            }

            override fun receiveDetections(detections: Detector.Detections<Barcode>) {
                val qrCodes = detections.detectedItems
                if (qrCodes.size() != 0) {
                    txtQrValue.post {
                        btnConnect.text = getString(R.string.connect_to_receiver)
                        mQrCodeValue = qrCodes.valueAt(0).displayValue
                        txtQrValue.text = mQrCodeValue
                    }
                }
            }
        })
    }

    override fun onEndpointDiscovered(endpoint: Endpoint) {
        stopDiscovering()
        connectToEndpoint(endpoint)
    }

    override fun onConnectionInitiated(
        endpoint: Endpoint,
        connectionInfo: ConnectionInfo
    ) {
        acceptConnection(endpoint)
    }

    override fun onEndpointConnected(endpoint: Endpoint) {
        Toast.makeText(
            this, getString(R.string.toast_connected, endpoint.name), Toast.LENGTH_SHORT
        )
            .show()
        setState(State.CONNECTED)
    }

    override fun onEndpointDisconnected(endpoint: Endpoint) {
        Toast.makeText(
            this,
            getString(R.string.toast_disconnected, endpoint.name),
            Toast.LENGTH_SHORT
        )
            .show()
        setState(State.SEARCHING)
    }

    override fun onConnectionFailed(endpoint: Endpoint?) {
        if (mState == State.SEARCHING) {
            startDiscovering()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Adding menu to activity
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // onClick listener for menu
        when (item.itemId) {
            R.id.home -> {
                setState(State.UNKNOWN)
                // home icon got clicked -> open welcome activity
                val homeIntent = Intent(this, Welcome::class.java)
                homeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(homeIntent)
            }

            R.id.settings -> {
                setState(State.UNKNOWN)
                // gear icon got clicked -> open settings activity
                val intent = Intent(this, Settings::class.java)
                startActivity(intent)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        super.onBackPressed()
        setState(State.UNKNOWN)
    }

    /** Starts recording sound from the microphone and streaming it to all connected devices. */
    private fun startRecording() {
        println("Connection.startRecording()")
        try {
            val payloadPipe = ParcelFileDescriptor.createPipe()

            // Send the first half of the payload (the read side) to Nearby Connections.
            send(Payload.fromStream(payloadPipe[0]))

            // Use the second half of the payload (the write side) in AudioRecorder.
            mRecorder = AudioRecorder(payloadPipe[1])
            mRecorder!!.start()
        } catch (e: IOException) {
            println("Connection.startRecording() failed $e")
        }

    }

    /** Stops streaming sound from the microphone. */
    private fun stopRecording() {
        println("stopRecording()")
        if (mRecorder != null) {
            mRecorder!!.stop()
            mRecorder = null
        }
    }

    /** @return True if currently streaming from the microphone. */
    private fun isRecording(): Boolean {
        return mRecorder != null && mRecorder!!.isRecording()
    }

    /** Stops all currently streaming audio tracks. */
    private fun stopPlaying() {
        println("stopPlaying()")
        if (mAudioPlayer != null) {
            mAudioPlayer!!.stop()
            mAudioPlayer = null
        }
    }

    /** @return True if currently playing. */
    private fun isPlaying(): Boolean {
        return mAudioPlayer != null
    }
}
