package com.sleipold.dreamstream

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.ParcelFileDescriptor
import android.os.Process.THREAD_PRIORITY_AUDIO
import android.os.Process.setThreadPriority
import java.io.*
import kotlin.math.sqrt

/**
 * When created, you must pass a ParcelFileDescriptor. Once start() is called, the
 * file descriptor will be written to until stop() is called. */
class AudioRecorder constructor(file: ParcelFileDescriptor) {

    /** The stream to write to. */
    private val mOutputStream: OutputStream = ParcelFileDescriptor.AutoCloseOutputStream(file)

    /**
     * If true, the background thread will continue to loop and record audio. Once false, the thread
     * will shut down.
     */
    @Volatile
    private var mAlive: Boolean = false

    /** The background thread recording audio. */
    private var mThread: Thread? = null

    /** Threshold which has to be exceeded to start recording */
    var mAudioRecordThreshold: Int = 50

    fun start() {
        if (isRecording()) {
            println("AudioRecorder already running")
            return
        }

        mAlive = true
        mThread = Thread {
            println("${Thread.currentThread()} AudioRecorder has run.")
            setThreadPriority(THREAD_PRIORITY_AUDIO)

            val buffer = Buffer()
            val record = AudioRecord(
                MediaRecorder.AudioSource.DEFAULT,
                buffer.sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                buffer.size
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                println("AudioRecorder Thread.run() failed")
                mAlive = false
                return@Thread
            }

            record.startRecording()
            // While we're running, we'll read the bytes from the AudioRecord and write them
            // to our output stream.
            try {
                var volume: Int
                while (mAlive) {
                    val len = record.read(buffer.data, 0, buffer.size)
                    var sum = 0.0
                    if (len >= 0 && len <= buffer.size) {
                        for (i in 0 until len) {
                            sum += buffer.data[i] * buffer.data[i]
                        }
                        val amplitude = sum / len
                        volume = sqrt(amplitude).toInt()
                        if (volume >= mAudioRecordThreshold) {
                            mOutputStream.write(buffer.data, 0, len)
                            mOutputStream.flush()
                        }
                    } else {
                        println("Unexpected length returned: $len")
                    }
                }
            } catch (e: IOException) {
                println("Exception with recording stream $e")
            } finally {
                stopInternal()
                try {
                    record.stop()
                } catch (e: IllegalStateException) {
                    println("Failed to stop AudioRecord $e")
                }

                record.release()
            }
        }
        mThread!!.start()
    }

    private fun stopInternal() {
        mAlive = false
        try {
            mOutputStream.close()
        } catch (e: IOException) {
            println("Failed to close output stream $e")
        }
    }

    /** Stops recording audio.  */
    fun stop() {
        stopInternal()
        try {
            mThread!!.join()
        } catch (e: InterruptedException) {
            println("Interrupted while joining AudioRecorder thread $e")
            Thread.currentThread().interrupt()
        }
    }

    /** @return True if actively recording. False otherwise.
     */
    fun isRecording(): Boolean {
        return mAlive
    }

    private class Buffer : AudioBuffer() {
        override fun validSize(size: Int): Boolean {
            return size != AudioRecord.ERROR && size != AudioRecord.ERROR_BAD_VALUE
        }

        override fun getMinBufferSize(sampleRate: Int): Int {
            return AudioRecord.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
        }
    }

}