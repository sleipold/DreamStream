package com.sleipold.dreamstream

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.nearby.connection.*
import java.util.*

interface IConnection {

    // member
    var mName: String
    var mServiceId: String
    val mStrategy: Strategy

    var mConnectionsClient: ConnectionsClient?

    // devices we've discovered near us 
    val mDiscoveredEndpoints: HashMap<String, Endpoint>

    // devices we have pending connections to - they will stay pending until we call .acceptConnection or .rejectConnection 
    val mPendingConnections: HashMap<String, Endpoint>

    // devices we are currently connected to 
    val mEstablishedConnections: HashMap<String, Endpoint>

    var mIsConnecting: Boolean
    var mIsDiscovering: Boolean
    var mIsAdvertising: Boolean

    // callback - connection to other device 
    val mConnectionLifecycleCallback: ConnectionLifecycleCallback

    // callback - payload sent from another device 
    val mPayloadCallback: PayloadCallback

    // someone has sent us data - override this method to act on the event 
    fun onReceive(endpoint: Endpoint?, payload: Payload)

    // called when the user has accepted (or denied) our permission request 
    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    )

    // called when advertising successfully starts 
    fun onAdvertisingStarted()

    // called when advertising fails to start 
    fun onAdvertisingFailed()

    // called when discovery successfully starts. 
    fun onDiscoveryStarted()

    // called when discovery fails to start 
    fun onDiscoveryFailed()

    // called when a pending connection with a remote endpoint is created 
    fun onConnectionInitiated(endpoint: Endpoint, connectionInfo: ConnectionInfo)

    // Called when a connection with this endpoint has failed 
    fun onConnectionFailed(endpoint: Endpoint?)

    // called when a remote endpoint is discovered 
    fun onEndpointDiscovered(endpoint: Endpoint)

    // called when someone has connected to us 
    fun onEndpointConnected(endpoint: Endpoint)

    // called when someone has disconnected 
    fun onEndpointDisconnected(endpoint: Endpoint)

    // helper functions 

    // Returns true if the app was granted all the permissions. Otherwise, returns false 
    fun hasPermissions(context: Context, permissions: Array<String>): Boolean {
        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        return true
    }

    fun startAdvertising() {
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
            println("Now advertising endpoint $localEndpointName on serviceId $mServiceId")
            onAdvertisingStarted()
        }?.addOnFailureListener { e ->
            mIsAdvertising = false
            println(String.format("startAdvertising() failed. %s", e.toString()))
            onAdvertisingFailed()
        }
    }

    fun stopAdvertising() {
        mIsAdvertising = false
        mConnectionsClient!!.stopAdvertising()
    }

    fun acceptConnection(endpoint: Endpoint) {
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

    fun rejectConnection(endpoint: Endpoint) {
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

    fun startDiscovering() {
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

    fun disconnect(endpoint: Endpoint) {
        mConnectionsClient!!.disconnectFromEndpoint(endpoint.id)
        mEstablishedConnections.remove(endpoint.id)
    }

    fun stopDiscovering() {
        mIsDiscovering = false
        mConnectionsClient!!.stopDiscovery()
    }

    // returns true if currently discovering 
    fun isDiscovering(): Boolean {
        return mIsDiscovering
    }

    // disconnects from all currently connected endpoints 
    fun disconnectFromAllEndpoints() {
        for (endpoint in mEstablishedConnections.values) {
            mConnectionsClient!!.disconnectFromEndpoint(endpoint.id)
        }
        mEstablishedConnections.clear()
    }

    // resets and clears all pState in Nearby Connections
    fun stopAllEndpoints() {
        mConnectionsClient!!.stopAllEndpoints()
        mIsAdvertising = false
        mIsDiscovering = false
        mIsConnecting = false
        mDiscoveredEndpoints.clear()
        mPendingConnections.clear()
        mEstablishedConnections.clear()
    }

    // sends a connection request to the endpoint 
    fun connectToEndpoint(endpoint: Endpoint) {
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

    fun connectedToEndpoint(endpoint: Endpoint?) {
        println(String.format("connectedToEndpoint(endpoint=%s)", endpoint!!))
        mEstablishedConnections[endpoint.id] = endpoint
        onEndpointConnected(endpoint)
    }

    fun disconnectedFromEndpoint(endpoint: Endpoint?) {
        println(String.format("disconnectedFromEndpoint(endpoint=%s)", endpoint!!))
        mEstablishedConnections.remove(endpoint.id)
        onEndpointDisconnected(endpoint)
    }

    // sends a payload to all currently connected endpoints 
    fun send(payload: Payload) {
        send(payload, mEstablishedConnections.keys)
    }

    fun send(payload: Payload, endpoints: Set<String>) {
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

    // helper class 

    class Endpoint(val id: String, val name: String) {

        override fun equals(obj: Any?): Boolean {
            if (obj is Endpoint) {
                val other = obj as Endpoint?
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
        AVAILABLE,
        SEARCHING,
        CONNECTED
    }

}