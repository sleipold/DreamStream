package com.sleipold.dreamstream

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import androidx.annotation.WorkerThread
import androidx.core.app.NotificationCompat
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.sleipold.dreamstream.IConnection.Endpoint
import com.sleipold.dreamstream.IConnection.State
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import java.io.IOException
import java.util.*

data class ThresholdChanged(val pThreshold: Int)
data class RecordVoiceMsgChanged(val pIsRecordingVoiceMsg: Boolean)

class ConnectionService : Service(), IConnection {

    // Service properties

    private var mIntent: Intent? = null
    private lateinit var mContext: Context
    private val mChannelId = "ForegroundServiceChannel"
    private var mDisposableThreshold: Disposable? = null
    private var mDisposableVoiceMsg: Disposable? = null

    // Interface properties

    override lateinit var mName: String
    override lateinit var mServiceId: String
    override val mStrategy: Strategy = Strategy.P2P_POINT_TO_POINT

    override var mIsConnecting: Boolean = false
    override var mIsDiscovering: Boolean = false
    override var mIsAdvertising: Boolean = false

    override var mConnectionsClient: ConnectionsClient? = null
    override val mDiscoveredEndpoints: HashMap<String, Endpoint> = HashMap()
    override val mPendingConnections: HashMap<String, Endpoint> = HashMap()
    override val mEstablishedConnections: HashMap<String, Endpoint> = HashMap()

    override val mConnectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            println(
                String.format(
                    "onConnectionInitiated(endpointId=%s, endpointName=%s)",
                    endpointId, connectionInfo.endpointName
                )
            )
            val endpoint = Endpoint(endpointId, connectionInfo.endpointName)
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
                    "onPayloadReceived(endpointId=%s, payload=%s): on $mName",
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
                        "onPayloadTransferUpdate(endpointId=%s, update=%s) successful: on $mName",
                        endpointId,
                        update
                    )
                )
            }
        }
    }

    // Audio properties

    private var mRecorder: AudioRecorder? = null
    private var mAudioPlayer: AudioPlayer? = null
    private var mOriginalVolume: Int = 0
    private var mVolumeControlStream: Int = AudioManager.STREAM_MUSIC

    // properties

    private var mState = State.UNKNOWN
    private var mReceiverIsRecordingVoiceMsg = false

    // Service functions

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        mIntent = intent
        mName = mIntent!!.getStringExtra("role")!!
        mServiceId = mIntent!!.getStringExtra("serviceId")!!
        mContext = applicationContext
        mConnectionsClient = Nearby.getConnectionsClient(mContext)

        println("ConnectionService started: on $mName")

        // set the media volume to max and store original volume
        mVolumeControlStream = AudioManager.STREAM_MUSIC
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // muting all other audio streams
        audioManager.setStreamMute(AudioManager.STREAM_SYSTEM, true)
        audioManager.setStreamMute(AudioManager.STREAM_ALARM, true)
        audioManager.setStreamMute(AudioManager.STREAM_NOTIFICATION, true)
        audioManager.setStreamMute(AudioManager.STREAM_RING, true)
        audioManager.setStreamMute(AudioManager.STREAM_VOICE_CALL, true)

        mOriginalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0
        )

        mDisposableThreshold =
            EventBus.subscribe<ThresholdChanged>()
                // receive event on main thread
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    println("$mName: event received: $it")
                    send(Payload.fromBytes(("threshold;" + it.pThreshold.toString()).toByteArray()))
                }

        mDisposableVoiceMsg =
            EventBus.subscribe<RecordVoiceMsgChanged>()
                // receive event on main thread
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    println("$mName: event received: $it")
                    mReceiverIsRecordingVoiceMsg = it.pIsRecordingVoiceMsg
                    // disable player and enable recorder (at receiver)
                    if (mReceiverIsRecordingVoiceMsg && mAudioPlayer != null && mAudioPlayer!!.isPlaying()) {
                        stopPlaying()
                        startRecording()
                    } else {
                        // disable recorder and enable player (at receiver)
                        stopRecording()
                    }
                    send(Payload.fromBytes(("voice;" + it.pIsRecordingVoiceMsg.toString()).toByteArray()))
                }

        val contentTitle = "ConnectionService"
        val contentText = if (mName == "receiver") {
            "Playing audio"
        } else {
            "Recording audio"
        }

        createNotificationChannel()
        val notificationIntent = Intent(this, Connection::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0, notificationIntent, 0
        )

        val notification = NotificationCompat.Builder(this, mChannelId)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_dreamstream_logo)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1, notification)

        setState(State.SEARCHING)

        // if service gets killed, after returning from here, restart
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        // don't provide binding, so return null
        return null
    }

    override fun onDestroy() {
        // restore the original volume
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, mOriginalVolume, 0)
        mVolumeControlStream = AudioManager.USE_DEFAULT_STREAM_TYPE

        // unmute all other audio streams
        //audioManager.setStreamMute(AudioManager.STREAM_SYSTEM, false)
        audioManager.setStreamMute(AudioManager.STREAM_ALARM, false)
        //audioManager.setStreamMute(AudioManager.STREAM_NOTIFICATION, false)
        //audioManager.setStreamMute(AudioManager.STREAM_RING, false)
        audioManager.setStreamMute(AudioManager.STREAM_VOICE_CALL, false)

        if (isRecording()) {
            stopRecording()
        }

        if (isPlaying()) {
            stopPlaying()
        }

        mDisposableThreshold?.dispose()
        mDisposableVoiceMsg?.dispose()

        disconnectFromAllEndpoints()
        stopAllEndpoints()

        println("ConnectionService destroyed: on $mName")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                mChannelId,
                "Foreground Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )

            val manager = getSystemService(NotificationManager::class.java)
            manager!!.createNotificationChannel(serviceChannel)
        }
    }

    // Interface functions

    override fun onReceive(endpoint: Endpoint?, payload: Payload) {
        println("ConnectionService onReceive: on $mName")
        when (payload.type) {
            Payload.Type.STREAM -> {
                if (mAudioPlayer != null) {
                    mAudioPlayer!!.stop()
                    mAudioPlayer = null
                }

                val player =
                    object : AudioPlayer(payload.asStream()!!.asInputStream(), mContext) {
                        @WorkerThread
                        override fun onFinish() {
                            mAudioPlayer = null
                        }
                    }
                mAudioPlayer = player
                player.start()
            }

            Payload.Type.BYTES -> {
                val receivedBytes = payload.asBytes()
                val parts = String(receivedBytes!!).split(";")
                when (parts[0]) {
                    "voice" -> {
                        mReceiverIsRecordingVoiceMsg = parts[1].toBoolean()
                        if (mReceiverIsRecordingVoiceMsg) {
                            // disable recorder and enable player (at sender)
                            stopRecording()
                        } else {
                            // disable player and enable recorder (at sender)
                            stopPlaying()
                            startRecording()
                        }
                    }
                    "threshold" -> {
                        if (isRecording()) {
                            // sender
                            // set audio threshold according to receivers seekbar
                            mRecorder!!.mAudioRecordThreshold = parts[1].toInt()
                        }
                    }
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
    }

    override fun onAdvertisingStarted() {
        println("ConnectionService onAdvertisingStarted: on $mName")
    }

    override fun onAdvertisingFailed() {
        println("ServiceConnection onAdvertisingFailed: on $mName")
    }

    override fun onDiscoveryStarted() {
        println("ServiceConnection onDiscoveryStarted: on $mName")
    }

    override fun onDiscoveryFailed() {
        println("ServiceConnection onDiscoveryFailed: on $mName")
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
        println("ConnectionService onEndpointConnected: connected to endpoint $endpoint")
        setState(State.CONNECTED)
    }

    override fun onEndpointDisconnected(endpoint: Endpoint) {
        println("ConnectionService onEndpointDisconnected: disconnected from endpoint $endpoint")
        setState(State.AVAILABLE)
    }

    override fun onConnectionFailed(endpoint: Endpoint?) {
        if (mState == State.SEARCHING) {
            startDiscovering()
        }
    }

// functions

    private fun setState(pState: State) {
        if (mState == pState) {
            println("ConnectionService: State of $mName set to $pState but device was already in this pState.")
            return
        }

        mState = pState
        println("ConnectionService: State of $mName set to $mState")
        onStateChanged(mState)
        onNewMessage(mState)
    }

    private fun onStateChanged(pState: State) {
        // update nearby connections to the new pState
        when (pState) {
            State.AVAILABLE -> {
                disconnectFromAllEndpoints()
            }
            State.SEARCHING -> {
                disconnectFromAllEndpoints()
                startDiscovering()
                startAdvertising()
            }
            State.CONNECTED -> {
                stopDiscovering()
                stopAdvertising()
                when (mName) {
                    "sender" -> {
                        startRecording()
                    }
                }
            }
            State.UNKNOWN -> {}
        }
    }

    private fun onNewMessage(pState: State) {
        EventBus.post(StateChanged(pState))
    }

    /** Starts recording sound from the microphone and streaming it to all connected devices. */
    private fun startRecording() {
        println("ConnectionService.startRecording(): on $mName")
        try {
            val payloadPipe = ParcelFileDescriptor.createPipe()

            // Send the first half of the payload (the read side) to Nearby Connections.
            send(Payload.fromStream(payloadPipe[0]))

            // Use the second half of the payload (the write side) in AudioRecorder.
            mRecorder = AudioRecorder(payloadPipe[1])
            mRecorder!!.start()
        } catch (e: IOException) {
            println("Connection.startRecording(): on $mName failed $e")
        }

    }

    /** Stops streaming sound from the microphone. */
    private fun stopRecording() {
        println("stopRecording(): on $mName")
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
        println("stopPlaying(): on $mName")
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
