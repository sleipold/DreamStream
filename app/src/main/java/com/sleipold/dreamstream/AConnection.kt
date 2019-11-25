package com.sleipold.dreamstream

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import java.util.ArrayList
import java.util.HashMap

abstract class AConnection : AppCompatActivity() {

    /* constants */

    private val REQUEST_CODE_REQUIRED_PERMISSIONS = 1

    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.CHANGE_WIFI_STATE,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    /* member */

    abstract var mName: String
    abstract val mServiceId: String
    abstract val mStrategy: Strategy

    private var mConnectionsClient: ConnectionsClient? = null

    /* devices we've discovered near us */
    private val mDiscoveredEndpoints = HashMap<String, Endpoint>()

    /* devices we have pending connections to - they will stay pending until we call .acceptConnection or .rejectConnection */
    private val mPendingConnections = HashMap<String, Endpoint>()

    /* devices we are currently connected to */
    private val mEstablishedConnections = HashMap<String, Endpoint>()

    private var mIsConnecting = false
    private var mIsDiscovering = false
    private var mIsAdvertising = false

    /* callback - connection to other device */

    private val mConnectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            println(
                String.format(
                    "onConnectionInitiated(endpointId=%s, endpointName=%s)",
                    endpointId, connectionInfo.endpointName
                )
            )
            val endpoint = Endpoint(endpointId, connectionInfo.endpointName)
            mPendingConnections[endpointId] = endpoint
            this@AConnection.onConnectionInitiated(endpoint, connectionInfo)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            println(
                String.format(
                    "onConnectionResponse(endpointId=%s, result=%s)",
                    endpointId,
                    result
                )
            )

            // We're no longer connecting
            mIsConnecting = false

            if (!result.status.isSuccess) {
                println(
                    String.format(
                        "Connection failed. Received status %s.",
                        result.status
                    )
                )
                onConnectionFailed(mPendingConnections.remove(endpointId))
                return
            }
            connectedToEndpoint(mPendingConnections.remove(endpointId))
        }

        override fun onDisconnected(endpointId: String) {
            if (!mEstablishedConnections.containsKey(endpointId)) {
                println("Unexpected disconnection from endpoint $endpointId")
                return
            }
            disconnectedFromEndpoint(mEstablishedConnections[endpointId])
        }
    }

    /* callback - payload sent from another device */

    private val mPayloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            println(
                String.format(
                    "onPayloadReceived(endpointId=%s, payload=%s)",
                    endpointId,
                    payload
                )
            )
            onReceive(mEstablishedConnections[endpointId], payload)
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                println(
                    String.format(
                        "onPayloadTransferUpdate(endpointId=%s, update=%s) successful", endpointId, update
                    )
                )
            }
        }
    }

    /* event functions which have to be implemented */

    /* someone has sent us data - override this method to act on the event */
    protected open fun onReceive(endpoint: Endpoint?, payload: Payload) {}

    /* event functions */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mConnectionsClient = Nearby.getConnectionsClient(this)
    }

    override fun onStart() {
        super.onStart()
        if (!hasPermissions(this, REQUIRED_PERMISSIONS)) {
            if (!hasPermissions(this, REQUIRED_PERMISSIONS)) {
                if (Build.VERSION.SDK_INT < 23) {
                    ActivityCompat.requestPermissions(
                        this, REQUIRED_PERMISSIONS, REQUEST_CODE_REQUIRED_PERMISSIONS
                    )
                } else {
                    requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_REQUIRED_PERMISSIONS)
                }
            }
        }
    }

    /* called when the user has accepted (or denied) our permission request */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_REQUIRED_PERMISSIONS) {
            for (grantResult in grantResults) {
                if (grantResult == PackageManager.PERMISSION_DENIED) {
                    Toast.makeText(this, R.string.error_missing_permissions, Toast.LENGTH_LONG)
                        .show()
                    finish()
                    return
                }
            }
            recreate()
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    /* called when advertising successfully starts */
    protected fun onAdvertisingStarted() {}

    /* called when advertising fails to start */
    protected fun onAdvertisingFailed() {}

    /* called when discovery successfully starts. */
    protected fun onDiscoveryStarted() {}

    /* called when discovery fails to start */
    protected fun onDiscoveryFailed() {}

    /* called when a pending connection with a remote endpoint is created */
    protected open fun onConnectionInitiated(endpoint: Endpoint, connectionInfo: ConnectionInfo) {}

    /* Called when a connection with this endpoint has failed */
    protected open fun onConnectionFailed(endpoint: Endpoint?) {}

    /* called when a remote endpoint is discovered */
    protected open fun onEndpointDiscovered(endpoint: Endpoint) {}

    /* called when someone has connected to us */
    protected open fun onEndpointConnected(endpoint: Endpoint) {}

    /* called when someone has disconnected */
    protected open fun onEndpointDisconnected(endpoint: Endpoint) {}

    /* helper functions */

    /* Returns true if the app was granted all the permissions. Otherwise, returns false */
    private fun hasPermissions(context: Context, permissions: Array<String>): Boolean {
        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    permission
                ) !== PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        return true
    }

    protected fun startAdvertising() {
        mIsAdvertising = true
        val localEndpointName = mName

        val advertisingOptions = AdvertisingOptions.Builder()
        advertisingOptions.setStrategy(mStrategy)

        mConnectionsClient?.startAdvertising(
            localEndpointName,
            mServiceId,
            mConnectionLifecycleCallback,
            advertisingOptions.build()
        )?.addOnSuccessListener {
            println("Now advertising endpoint $localEndpointName")
            onAdvertisingStarted()
        }?.addOnFailureListener { e ->
            mIsAdvertising = false
            println(String.format("startAdvertising() failed. %s", e.toString()))
            onAdvertisingFailed()
        }
    }

    protected fun stopAdvertising() {
        mIsAdvertising = false
        mConnectionsClient!!.stopAdvertising()
    }

    protected fun acceptConnection(endpoint: Endpoint) {
        mConnectionsClient?.acceptConnection(endpoint.id, mPayloadCallback)
            ?.addOnFailureListener { e ->
                println(
                    String.format(
                        "acceptConnection() failed. %s",
                        e.toString()
                    )
                )
            }
    }

    protected fun rejectConnection(endpoint: Endpoint) {
        mConnectionsClient?.rejectConnection(endpoint.id)
            ?.addOnFailureListener { e ->
                println(
                    String.format(
                        "rejectConnection() failed. %s",
                        e.toString()
                    )
                )
            }
    }

    protected fun startDiscovering() {
        mIsDiscovering = true
        mDiscoveredEndpoints.clear()
        val discoveryOptions = DiscoveryOptions.Builder()
        discoveryOptions.setStrategy(mStrategy)
        mConnectionsClient?.startDiscovery(
            mServiceId,
            object : EndpointDiscoveryCallback() {
                override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                    println(
                        String.format(
                            "onEndpointFound(endpointId=%s, serviceId=%s, endpointName=%s)",
                            endpointId, info.serviceId, info.endpointName
                        )
                    )

                    if (mServiceId == info.serviceId) {
                        val endpoint = Endpoint(endpointId, info.endpointName)
                        mDiscoveredEndpoints[endpointId] = endpoint
                        onEndpointDiscovered(endpoint)
                    }
                }

                override fun onEndpointLost(endpointId: String) {
                    println(String.format("onEndpointLost(endpointId=%s)", endpointId))
                }
            },
            discoveryOptions.build()
        )?.addOnSuccessListener { onDiscoveryStarted() }?.addOnFailureListener { e ->
            mIsDiscovering = false
            println(
                String.format("startDiscovering() failed. %s", e.toString())
            )
            onDiscoveryFailed()
        }
    }

    protected fun disconnect(endpoint: Endpoint) {
        mConnectionsClient!!.disconnectFromEndpoint(endpoint.id)
        mEstablishedConnections.remove(endpoint.id)
    }

    protected fun stopDiscovering() {
        mIsDiscovering = false
        mConnectionsClient!!.stopDiscovery()
    }

    /* returns true if currently discovering */
    protected fun isDiscovering(): Boolean {
        return mIsDiscovering
    }

    /* disconnects from all currently connected endpoints */
    protected fun disconnectFromAllEndpoints() {
        for (endpoint in mEstablishedConnections.values) {
            mConnectionsClient!!.disconnectFromEndpoint(endpoint.id)
        }
        mEstablishedConnections.clear()
    }

    /* resets and clears all state in Nearby Connections */
    protected fun stopAllEndpoints() {
        mConnectionsClient!!.stopAllEndpoints()
        mIsAdvertising = false
        mIsDiscovering = false
        mIsConnecting = false
        mDiscoveredEndpoints.clear()
        mPendingConnections.clear()
        mEstablishedConnections.clear()
    }

    /* sends a connection request to the endpoint */
    protected fun connectToEndpoint(endpoint: Endpoint) {
        println("Sending a connection request to endpoint $endpoint")
        // Mark ourselves as connecting so we don't connect multiple times
        mIsConnecting = true

        // Ask to connect
        mConnectionsClient?.requestConnection(mName, endpoint.id, mConnectionLifecycleCallback)
            ?.addOnFailureListener { e ->
                println(String.format("requestConnection() failed. %s", e.toString()))
                mIsConnecting = false
                onConnectionFailed(endpoint)
            }
    }

    private fun connectedToEndpoint(endpoint: Endpoint?) {
        println(String.format("connectedToEndpoint(endpoint=%s)", endpoint!!))
        mEstablishedConnections[endpoint.id] = endpoint
        onEndpointConnected(endpoint)
    }

    private fun disconnectedFromEndpoint(endpoint: Endpoint?) {
        println(String.format("disconnectedFromEndpoint(endpoint=%s)", endpoint!!))
        mEstablishedConnections.remove(endpoint.id)
        onEndpointDisconnected(endpoint)
    }

    /* sends a payload to all currently connected endpoints */
    protected fun send(payload: Payload) {
        send(payload, mEstablishedConnections.keys)
    }

    private fun send(payload: Payload, endpoints: Set<String>) {
        mConnectionsClient?.sendPayload(ArrayList(endpoints), payload)
            ?.addOnFailureListener { e ->
                println(
                    String.format(
                        "sendPayload() failed. %s",
                        e.toString()
                    )
                )
            }
    }

    /* helper class */

    class Endpoint(val id: String, val name: String) {

        override fun equals(other: Any?): Boolean {
            if (other is Endpoint) {
                val other = other as Endpoint?
                return id == other!!.id
            }
            return false
        }

        override fun hashCode(): Int {
            return id.hashCode()
        }

        override fun toString(): String {
            return String.format("Endpoint{id=%s, name=%s}", id, name)
        }
    }

    enum class State {
        UNKNOWN,
        SEARCHING,
        CONNECTED
    }

}