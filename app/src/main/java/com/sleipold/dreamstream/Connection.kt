package com.sleipold.dreamstream

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Point
import android.os.Bundle
import android.text.InputType
import android.view.*
import android.widget.*
import androidmads.library.qrgenearator.QRGContents
import androidmads.library.qrgenearator.QRGEncoder
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import com.google.android.gms.vision.CameraSource
import com.google.android.gms.vision.Detector
import com.google.android.gms.vision.barcode.Barcode
import com.google.android.gms.vision.barcode.BarcodeDetector
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.zxing.WriterException
import com.sleipold.dreamstream.IConnection.State
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.activity_connection.*
import java.io.IOException
import java.util.*

data class StateChanged(val pState: State)

class Connection : AppCompatActivity() {
    // member
    private lateinit var mContext: Context
    private val mRequestCameraPermission = 201

    private lateinit var mName: String
    private lateinit var mServiceId: String

    private var mState = State.UNKNOWN
    private var mIsRecordingVoiceMsg = false

    private var mDisposable: Disposable? = null

    private var mQrCodeDetector: BarcodeDetector? = null
    private var mCameraSource: CameraSource? = null
    private var mQrCodeValue: String = ""
    private var mQrCodeBitmap: Bitmap? = null

    // components
    // both
    private lateinit var cCurrentState: TextView
    private lateinit var cRoleName: TextView
    private lateinit var cHandleConnection: Button

    // receiver
    private lateinit var cAudioRecordThreshold: SeekBar
    private lateinit var cQrCode: ImageView
    private lateinit var cQrCodeInfo: TextView
    private lateinit var cVoiceMsg: FloatingActionButton

    // sender
    private lateinit var cCamera: SurfaceView
    private lateinit var cQrCodeValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connection)

        // member 
        mContext = applicationContext
        mName = intent.getStringExtra("role")!!
        val uid = UUID.randomUUID().toString()
        mServiceId = "dreamstream$uid"

        // components 
        // used by both
        cCurrentState = findViewById(R.id.txtCurrState)
        cRoleName = findViewById(R.id.txtRoleName)
        cHandleConnection = findViewById(R.id.btnHandleConnection)

        // used by receiver
        cAudioRecordThreshold = findViewById(R.id.sbAudioRecordThreshold)
        cQrCode = findViewById(R.id.ivQrImage)
        cQrCodeInfo = findViewById(R.id.txtScannerInfo)
        cVoiceMsg = findViewById(R.id.btnVoiceMsg)

        // used by sender
        cCamera = findViewById(R.id.svScreen)
        cQrCodeValue = findViewById(R.id.txtQrValue)

        cRoleName.text = mName

        mDisposable =
            EventBus.subscribe<StateChanged>()
                // receive event on main thread
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    println("$mName: event received: $it")
                    setState(it.pState)
                }

        when (mName) {
            "receiver" -> {
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
                        // send audio record threshold to service
                        onNewMessage(seekBar.progress)
                    }
                })

                cVoiceMsg.setOnClickListener {
                    mIsRecordingVoiceMsg = !mIsRecordingVoiceMsg
                    onNewMessage(mIsRecordingVoiceMsg)
                }
            }
        }

        cHandleConnection.setOnClickListener {
            if (mState == State.SEARCHING || mState == State.CONNECTED) {
                // disconnect
                setState(State.AVAILABLE)
            } else {
                // connect
                if (mQrCodeValue.isNotEmpty()) {
                    setState(State.SEARCHING)
                }
            }
        }

        setState(State.AVAILABLE)
    }

    override fun onBackPressed() {
        moveTaskToBack(true)
    }

    override fun onDestroy() {
        super.onDestroy()
        mDisposable?.dispose()

        if (mName == "sender") {
            mCameraSource!!.release()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Adding menu to activity
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.home -> {
                val homeIntent = Intent(this, Welcome::class.java)
                startActivity(homeIntent)
            }

            R.id.settings -> {
                // gear icon got clicked -> open settings activity
                val intent = Intent(this, Settings::class.java)
                startActivity(intent)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun onStateChanged(newState: State) {
        // update nearby connections to the new state
        // and handles visibility of components

        // both
        cHandleConnection.isVisible = false

        // sender components
        cCamera.isVisible = false
        cQrCodeValue.isVisible = false

        // recorder components
        cAudioRecordThreshold.isVisible = false
        cQrCode.isVisible = false
        cQrCodeInfo.isVisible = false
        cVoiceMsg.isVisible = false

        when (newState) {
            State.AVAILABLE -> {
                cCurrentState.setText(R.string.state_available)

                // stops service when clicked -> onDestroy of service is called which closes nearby connections
                Intent(this, ConnectionService::class.java).also { intent -> stopService(intent) }

                when (mName) {
                    "receiver" -> {
                        setServiceIdFromUserInput()
                    }
                    "sender" -> {
                        cCamera.isVisible = true
                        cQrCodeValue.isVisible = true
                        cQrCodeValue.setText(R.string.no_qr_code_detected)
                        readQrCode()
                    }
                }
            }
            State.SEARCHING -> {
                // start connection service
                Intent(this, ConnectionService::class.java).also { intent ->
                    run {
                        intent.putExtra("role", mName)
                        intent.putExtra("serviceId", mServiceId)
                        startService(intent)
                    }
                }
                when (mName) {
                    "receiver" -> {
                        cQrCode.isVisible = true
                    }
                }
                cHandleConnection.setText(R.string.disconnect)
                cHandleConnection.isVisible = true
                cCurrentState.setText(R.string.state_searching)
            }
            State.CONNECTED -> {
                cCurrentState.setText(R.string.state_connected)
                cHandleConnection.setText(R.string.disconnect)
                cHandleConnection.isVisible = true

                when (mName) {
                    "receiver" -> {
                        cAudioRecordThreshold.progress = 50
                        cAudioRecordThreshold.isVisible = true
                        cVoiceMsg.isVisible = true
                        cQrCode.isVisible = false
                        cQrCodeInfo.isVisible = false
                    }
                }
            }
            State.UNKNOWN -> {
                cCurrentState.setText(R.string.state_unknown)
                // stops service when clicked -> onDestroy of service is called which closes nearby connections
                Intent(this, ConnectionService::class.java).also { intent -> stopService(intent) }
            }
        }
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

    private fun readQrCode() {
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
                            mRequestCameraPermission
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
                        cHandleConnection.isVisible = true
                        cHandleConnection.text = getString(R.string.connect_to_receiver)
                        mQrCodeValue = qrCodes.valueAt(0).displayValue
                        txtQrValue.text = mQrCodeValue
                        mServiceId = mQrCodeValue
                    }
                }
            }
        })
    }

    private fun getQrCode(): Bitmap? {
        if (mServiceId.isNotEmpty()) {
            val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            val display = windowManager.defaultDisplay
            val point = Point()
            display.getSize(point)
            val width = point.x / 2
            val height = point.y / 2
            var smallerDimension = if (width < height) width else height
            smallerDimension = smallerDimension * 3 / 4

            val qrgEncoder = QRGEncoder(
                mServiceId, null,
                QRGContents.Type.TEXT,
                smallerDimension
            )
            try {
                return qrgEncoder.encodeAsBitmap()
            } catch (e: WriterException) {
                println(e.toString())
            }
        }
        return null
    }

    private fun setServiceIdFromUserInput() {
        val builder = MaterialAlertDialogBuilder(this)
        builder.setTitle("Enter a connection name:")

        // Set up the input
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
        builder.setView(input)
        builder.setCancelable(false)

        // Set up the buttons
        builder.setPositiveButton("OK") { _, _ ->
            run {
                if (input.text.toString().isNotEmpty()) {
                    mServiceId = input.text.toString()
                }
                mQrCodeBitmap = getQrCode()
                if (mQrCodeBitmap != null) {
                    cQrCode.setImageBitmap(mQrCodeBitmap)
                    cQrCode.isVisible = true
                    cQrCodeInfo.isVisible = true
                    setState(State.SEARCHING)
                }
            }
        }
        builder.setNegativeButton("Close") { _, _ ->
            run {
                finish()
            }
        }

        builder.show()
    }

    private fun onNewMessage(pThreshold: Int) {
        EventBus.post(ThresholdChanged(pThreshold))
    }

    private fun onNewMessage(pRecordingVoiceMsg: Boolean) {
        // send message from activity to service
        EventBus.post(RecordVoiceMsgChanged(pRecordingVoiceMsg))
    }

}
