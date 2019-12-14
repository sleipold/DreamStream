package com.sleipold.dreamstream

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Process.THREAD_PRIORITY_AUDIO
import android.os.Process.setThreadPriority
import android.os.VibrationEffect
import android.os.Vibrator
import java.io.IOException
import java.io.InputStream
import kotlin.math.sqrt

/**
 * A fire-once class. When created, you must pass a {@link InputStream}. Once {@link #start()} is
 * called, the input stream will be read from until either {@link #stop()} is called or the stream
 * ends.
 */
open class AudioPlayer constructor(inputStream: InputStream, context: Context) {

    /** The audio stream we're reading from.  */
    private val mInputStream: InputStream = inputStream

    private val mContext: Context = context

    private val mSharedPrefs: SharedPreferences =
        mContext.getSharedPreferences(R.string.sharedPref.toString(), Context.MODE_PRIVATE)

    /**
     * If true, the background thread will continue to loop and play audio. Once false, the thread
     * will shut down.
     */
    @Volatile
    private var mAlive: Boolean = false

    /** The background thread recording audio for us.  */
    private var mThread: Thread? = null

    /** @return True if currently playing. */
    fun isPlaying(): Boolean {
        return mAlive
    }

    /** Starts playing the stream.  */
    fun start() {
        mAlive = true
        mThread = Thread {
            println("${Thread.currentThread()} AudioPlayer has run.")
            setThreadPriority(THREAD_PRIORITY_AUDIO)

            val buffer = Buffer()
            val audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                buffer.sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                buffer.size,
                AudioTrack.MODE_STREAM
            )
            audioTrack.play()

            try {
                var len: Int
                var sum = 0.0
                var volume: Int
                do {
                    len = mInputStream.read(buffer.data)
                    if (isPlaying() && len > 0) {
                        for (i in 0 until len) {
                            sum += buffer.data[i] * buffer.data[i]
                        }
                        val amplitude = sum / len
                        volume = sqrt(amplitude).toInt()
                        audioTrack.write(buffer.data, 0, len)
                        // calc amplitude and check for vibrate setting
                        val vibration = mSharedPrefs.getBoolean("vibration", false)
                        if (vibration && volume >= mSharedPrefs.getInt("warnlevel", 100)) {
                            val vibrator =
                                mContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                            if (Build.VERSION.SDK_INT >= 26) {
                                vibrator.vibrate(
                                    VibrationEffect.createOneShot(
                                        100,
                                        VibrationEffect.DEFAULT_AMPLITUDE
                                    )
                                )
                            } else {
                                vibrator.vibrate(100)
                            }
                        }
                    } else {
                        break
                    }
                } while (true)
            } catch (e: IOException) {
                println("Exception with playing stream $e")
            } finally {
                stopInternal()
                audioTrack.release()
                onFinish()
            }
        }
        mThread!!.start()
    }

    private fun stopInternal() {
        mAlive = false
        try {
            mInputStream.close()
        } catch (e: IOException) {
            println("Failed to close input stream $e")
        }

    }

    /** Stops playing the stream.  */
    fun stop() {
        stopInternal()
        try {
            mThread!!.join()
        } catch (e: InterruptedException) {
            println("Interrupted while joining AudioRecorder thread $e")
            Thread.currentThread().interrupt()
        }

    }

    /** The stream has now ended.  */
    protected open fun onFinish() {}

    private class Buffer : AudioBuffer() {
        override fun validSize(size: Int): Boolean {
            return size != AudioTrack.ERROR && size != AudioTrack.ERROR_BAD_VALUE
        }

        override fun getMinBufferSize(sampleRate: Int): Int {
            return AudioTrack.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
        }
    }

}