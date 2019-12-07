package com.sleipold.dreamstream

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.ParcelFileDescriptor
import androidx.annotation.WorkerThread
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import java.io.IOException
import java.util.*

class ConnectionService : Service(), IConnection {

    // Service properties

    private var mIntent: Intent? = null
    private lateinit var mContext: Context

    // Interface properties

    override lateinit var mName: String
    override lateinit var mServiceId: String
    override val mStrategy: Strategy = Strategy.P2P_POINT_TO_POINT

    override var mIsConnecting: Boolean = false
    override var mIsDiscovering: Boolean = false
    override var mIsAdvertising: Boolean = false
        
    override var mConnectionsClient: ConnectionsClient? = null
    override val mDiscoveredEndpoints: HashMap<String, IConnection.Endpoint> = HashMap()
    override val mPendingConnections: HashMap<String, IConnection.Endpoint> = HashMap()
    override val mEstablishedConnections: HashMap<String, IConnection.Endpoint> = HashMap()

    override val mConnectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            println(
                String.format(
                    "onConnectionInitiated(endpointId=%s, endpointName=%s)",
                    endpointId, connectionInfo.endpointName
                )
            )
            val endpoint = IConnection.Endpoint(endpointId, connectionInfo.endpointName)
            mPendingConnections[endpointId] = endpoint
            this@ConnectionService.onConnectionInitiated(endpoint, connectionInfo)
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
    override val mPayloadCallback = object : PayloadCallback() {
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

    // Audio properties

    private var mRecorder: AudioRecorder? = null
    private var mAudioPlayer: AudioPlayer? = null

    // properties

    private var mState = IConnection.State.UNKNOWN

    // Service functions

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        println("ConnectionService started")

        mIntent = intent
        mName = mIntent!!.getStringExtra("role")!!
        mServiceId = mIntent!!.getStringExtra("serviceId")!!
        mContext = applicationContext
        mConnectionsClient = Nearby.getConnectionsClient(mContext)

        setState(IConnection.State.SEARCHING)

        // if service gets killed, after returning from here, restart
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        // don't provide binding, so return null
        return null
    }

    override fun onDestroy() {
        println("ConnectionService destroyed")
    }

    // Interface functions

    override fun onReceive(endpoint: IConnection.Endpoint?, payload: Payload) {
        println("ConnectionService onReceive")
        when (payload.type) {
            Payload.Type.STREAM -> {
                if (mAudioPlayer != null) {
                    mAudioPlayer!!.stop()
                    mAudioPlayer = null
                }

                val player = object : AudioPlayer(payload.asStream()!!.asInputStream(), mContext) {
                    @WorkerThread
                    override fun onFinish() {
                        mAudioPlayer = null
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {}

    override fun onAdvertisingStarted() {
        println("ServiceConnection onAdvertisingStarted")
    }

    override fun onAdvertisingFailed() {
        println("ServiceConnection onAdvertisingFailed")
    }

    override fun onDiscoveryStarted() {
        println("ServiceConnection onDiscoveryStarted")
    }

    override fun onDiscoveryFailed() {
        println("ServiceConnection onDiscoveryFailed")
    }

    override fun onEndpointDiscovered(endpoint: IConnection.Endpoint) {
        stopDiscovering()
        connectToEndpoint(endpoint)
    }

    override fun onConnectionInitiated(
        endpoint: IConnection.Endpoint,
        connectionInfo: ConnectionInfo
    ) {
        acceptConnection(endpoint)
    }

    override fun onEndpointConnected(endpoint: IConnection.Endpoint) {
        println("ConnectionService onEndpointConnected")
        setState(IConnection.State.CONNECTED)
    }

    override fun onEndpointDisconnected(endpoint: IConnection.Endpoint) {
        println("ConnectionService onEndpointDisconnected")
        setState(IConnection.State.AVAILABLE)
    }

    override fun onConnectionFailed(endpoint: IConnection.Endpoint?) {
        if (mState == IConnection.State.SEARCHING) {
            startDiscovering()
        }
    }

    // functions

    private fun setState(pState: IConnection.State) {
        if (mState == pState) {
            println("ConnectionService: State set to $pState but device was already in this state.")
            return
        }

        mState = pState
        println("ConnectionService: State set to $pState")
        onStateChanged(pState)
    }

    private fun onStateChanged(newState: IConnection.State) {
        // update nearby connections to the new state

        when (newState) {
            IConnection.State.AVAILABLE -> {
                disconnectFromAllEndpoints()
            }
            IConnection.State.SEARCHING -> {
                disconnectFromAllEndpoints()
                startDiscovering()
                startAdvertising()
            }
            IConnection.State.CONNECTED -> {
                stopDiscovering()
                stopAdvertising()
                when (mName) {
                    "sender" -> {
                        startRecording()
                    }
                }
            }
            IConnection.State.UNKNOWN -> {
                stopAllEndpoints()
            }
        }
    }

    /** Starts recording sound from the microphone and streaming it to all connected devices. */
    private fun startRecording() {
        println("ConnectionService.startRecording()")
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
