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
    internal lateinit var txtBarcodeValue: TextView
    private var barcodeDetector: BarcodeDetector? = null
    private var cameraSource: CameraSource? = null
    internal lateinit var btnAction: Button
    internal var intentData = ""
    internal var isEmail = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sender_connection)

        initViews()
    }

    private fun initViews() {
        txtBarcodeValue = findViewById(R.id.txtBarcodeValue)
        surfaceView = findViewById(R.id.surfaceView)
        btnAction = findViewById(R.id.btnAction)


        btnAction.setOnClickListener {
            if (intentData.length > 0) {
                /*if (isEmail)
                    startActivity(
                        Intent(
                            this@SenderConnection,
                            EmailActivity::class.java
                        ).putExtra("email_address", intentData)
                    )
                else {*/
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(intentData)))
                //}
            }
        }
    }

    private fun initialiseDetectorsAndSources() {

        Toast.makeText(applicationContext, "Barcode scanner started", Toast.LENGTH_SHORT).show()

        barcodeDetector = BarcodeDetector.Builder(this)
            .setBarcodeFormats(Barcode.ALL_FORMATS)
            .build()

        cameraSource = CameraSource.Builder(this, barcodeDetector!!)
            .setRequestedPreviewSize(1920, 1080)
            .setAutoFocusEnabled(true) //you should add this feature
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


        barcodeDetector!!.setProcessor(object : Detector.Processor<Barcode> {
            override fun release() {
                Toast.makeText(
                    applicationContext,
                    "To prevent memory leaks barcode scanner has been stopped",
                    Toast.LENGTH_SHORT
                ).show()
            }

            override fun receiveDetections(detections: Detector.Detections<Barcode>) {
                val barcodes = detections.detectedItems
                if (barcodes.size() != 0) {


                    txtBarcodeValue.post {
                        if (barcodes.valueAt(0).email != null) {
                            txtBarcodeValue.removeCallbacks(null)
                            intentData = barcodes.valueAt(0).email.address
                            txtBarcodeValue.text = intentData
                            isEmail = true
                            btnAction.text = "ADD CONTENT TO THE MAIL"
                        } else {
                            isEmail = false
                            btnAction.text = "LAUNCH URL"
                            intentData = barcodes.valueAt(0).displayValue
                            txtBarcodeValue.text = intentData

                        }
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
        private val REQUEST_CAMERA_PERMISSION = 201
    }
}
