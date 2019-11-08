package com.sleipold.dreamstream

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

import com.google.android.gms.vision.CameraSource
import com.google.android.gms.vision.Detector
import com.google.android.gms.vision.barcode.Barcode
import com.google.android.gms.vision.barcode.BarcodeDetector

import java.io.IOException

class SenderConnection : AppCompatActivity() {

    internal lateinit var surfaceView: SurfaceView
    internal lateinit var txtQrCodeValue: TextView
    private var qrCodeDetector: BarcodeDetector? = null
    private var cameraSource: CameraSource? = null
    internal lateinit var btnAction: Button
    internal var intentData = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sender_connection)

        initViews()
    }

    private fun initViews() {

        txtQrCodeValue = findViewById(R.id.txtBarcodeValue)
        surfaceView = findViewById(R.id.surfaceView)
        btnAction = findViewById(R.id.btnAction)

        btnAction.setOnClickListener {
            if (intentData.isNotEmpty()) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(intentData)))
            }
        }
    }

    private fun initialiseDetectorsAndSources() {

        Toast.makeText(applicationContext, getString(R.string.qr_scanner_started), Toast.LENGTH_SHORT).show()

        qrCodeDetector = BarcodeDetector.Builder(this)
            .setBarcodeFormats(Barcode.QR_CODE)
            .build()

        cameraSource = CameraSource.Builder(this, qrCodeDetector!!)
            .setRequestedPreviewSize(1920, 1080)
            .setAutoFocusEnabled(true)
            .build()

        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                try {
                    if (ActivityCompat.checkSelfPermission(
                            this@SenderConnection,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        cameraSource!!.start(surfaceView.holder)
                    } else {
                        ActivityCompat.requestPermissions(
                            this@SenderConnection,
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
                cameraSource!!.stop()
            }
        })

        qrCodeDetector!!.setProcessor(object : Detector.Processor<Barcode> {
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
                    txtQrCodeValue.post {
                        btnAction.text = getString(R.string.connect_to_receiver)
                        intentData = qrCodes.valueAt(0).displayValue
                        txtQrCodeValue.text = intentData
                    }
                }
            }
        })
    }

    override fun onPause() {
        super.onPause()
        cameraSource!!.release()
    }

    override fun onResume() {
        super.onResume()
        initialiseDetectorsAndSources()
    }

    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 201
    }
}
