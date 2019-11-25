package com.sleipold.dreamstream

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process.THREAD_PRIORITY_AUDIO
import android.os.Process.setThreadPriority
import java.io.IOException
import java.io.InputStream


/**
 * A fire-once class. When created, you must pass a {@link InputStream}. Once {@link #start()} is
 * called, the input stream will be read from until either {@link #stop()} is called or the stream
 * ends.
 */
open class AudioPlayer constructor(inputStream: InputStream) {

    /** The audio stream we're reading from.  */
    private var mInputStream: InputStream = inputStream

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
        mThread =
            object : Thread() {
                override fun run() {
                    println("AudioPlayer Thread.run()")
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
                        do {
                            len = mInputStream.read(buffer.data)
                            if (isPlaying() && len > 0) {
                                audioTrack.write(buffer.data, 0, len)
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