package com.sleipold.dreamstream

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidmads.library.qrgenearator.QRGContents
import androidmads.library.qrgenearator.QRGEncoder
import com.google.zxing.WriterException
import android.graphics.Point
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import androidx.core.app.ComponentActivity
import androidx.core.app.ComponentActivity.ExtraData
import androidx.core.content.ContextCompat.getSystemService
import android.icu.lang.UCharacter.GraphemeClusterBreak.T
import android.R.attr.name

class ReceiverConnection : AppCompatActivity() {
    private lateinit var connectionsClient: ConnectionsClient
    private lateinit var statusText: TextView

    private val inputValue = "foobar42"
    private val endpointName = "receiver"
    private val advertisingOptions = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build()

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            // A new payload is being sent over.
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Payload progress has updated.
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            // Automatically accept the connection on both sides.
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    // We're connected! Can now start sending and receiving data.
                    statusText.text = getString(R.string.status_connected_receiver)
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    // The connection was rejected by one or both sides.
                    statusText.text = getString(R.string.status_rejected_receiver)
                }
                else -> {
                    // The connection was broken before it was accepted.
                    statusText.text = getString(R.string.status_fail_receiver)
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            // We've been disconnected from this endpoint. No more data can be
            // sent or received.
            statusText.text = getString(R.string.status_disconnect_receiver)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receiver_connection)

        statusText = findViewById(R.id.txtStatus)

        connectionsClient = Nearby.getConnectionsClient(this)

        if (inputValue.isNotEmpty()) {
            var qrImage: ImageView = findViewById(R.id.ivQrImage)
            val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            val display = windowManager.defaultDisplay
            val point = Point()
            display.getSize(point)
            val width = point.x
            val height = point.y
            var smallerDimension = if (width < height) width else height
            smallerDimension = smallerDimension * 3 / 4

            var qrgEncoder = QRGEncoder(
                inputValue, null,
                QRGContents.Type.TEXT,
                smallerDimension
            )
            try {
                // generating QR code and displaying it
                var bitmap = qrgEncoder.encodeAsBitmap()
                qrImage.setImageBitmap(bitmap)

                // starting to advertise to find sender
                findSender()
            } catch (e: WriterException) {
                println(e.toString())
            }
        } else {
            println("inputValue must not be empty")
        }
    }

    /** Starts advertising for other device using Nearby Connections.  */
    private fun startAdvertising() {
        connectionsClient.startAdvertising(
                endpointName,
                packageName,
                connectionLifecycleCallback,
                advertisingOptions
            )
    }

    private fun findSender() {
        startAdvertising()
        statusText.text = getString(R.string.status_searching_sender)
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
                // home icon got clicked -> open welcome activity
                val homeIntent = Intent(this, Welcome::class.java)
                homeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                // TODO: reset running connection
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
}
