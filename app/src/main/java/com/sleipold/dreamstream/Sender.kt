package com.sleipold.dreamstream

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback

class Sender : AppCompatActivity() {

    private lateinit var receiverName: String
    private lateinit var opponentEndpointId: String

    private lateinit var connectionsClient: ConnectionsClient
    private lateinit var statusText: TextView

    private val endpointName = "sender"
    private val discoveryOptions =
        DiscoveryOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build()

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            // A new payload is being sent over.
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Payload progress has updated.
        }
    }

    // Callbacks for finding other devices
    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            println("onEndpointFound: endpoint found, connecting")
            connectionsClient.requestConnection(endpointName, endpointId, connectionLifecycleCallback)
        }

        override fun onEndpointLost(endpointId: String) {}
    }

    // Callbacks for connections to other devices
    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            println("onConnectionInitiated: accepting connection")
            connectionsClient.acceptConnection(endpointId, payloadCallback)
            receiverName = connectionInfo.endpointName
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                println("onConnectionResult: connection successful")

                connectionsClient.stopDiscovery()
                connectionsClient.stopAdvertising()

                opponentEndpointId = endpointId
                statusText.text = getString(R.string.status_connected_sender)
            } else {
                println("onConnectionResult: sender failed to connect")
            }
        }

        override fun onDisconnected(endpointId: String) {
            println("onDisconnected: disconnected from receiver")
            statusText.text = getString(R.string.status_disconnect_sender)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sender)

        // Get status text view from layout
        statusText = findViewById(R.id.txtStatus)

        // Get the Intent that started this activity and extract the string
        val connectionSecret = intent.getStringExtra(CONNECTION_SECRET)

        // Capture the layout's TextView and set the string as its text
        findViewById<TextView>(R.id.txtConnectionSecret).apply {
            text = connectionSecret
        }

        connectionsClient = Nearby.getConnectionsClient(this)

        // nearby discovery starting - searching for receiver
        findReceiver()
    }

    private fun findReceiver() {
        startDiscovery()
        statusText.text = getString(R.string.status_searching_receiver)
    }

    /** Starts looking for other device using Nearby Connections.  */
    private fun startDiscovery() {
        connectionsClient.startDiscovery(
            packageName,
            endpointDiscoveryCallback,
            discoveryOptions
        )
    }


}