package com.sleipold.dreamstream


/**
 * A buffer that grabs the smallest supported sample rate for [android.media.AudioTrack] and
 * [android.media.AudioRecord].
 */
abstract class AudioBuffer protected constructor() {

    internal val size: Int
    internal val sampleRate: Int
    internal val data: ByteArray

    init {
        var size = -1
        var sampleRate = -1

        // Iterate over all possible sample rates, and try to find the shortest one. The shorter
        // it is, the faster it'll stream.
        for (rate in POSSIBLE_SAMPLE_RATES) {
            sampleRate = rate
            size = getMinBufferSize(sampleRate)
            if (validSize(size)) {
                break
            }
        }

        // If none of them were good, then just pick 1kb
        if (!validSize(size)) {
            size = 1024
        }

        this.size = size
        this.sampleRate = sampleRate
        data = ByteArray(size)
    }

    protected abstract fun validSize(size: Int): Boolean

    protected abstract fun getMinBufferSize(sampleRate: Int): Int

    companion object {
        private val POSSIBLE_SAMPLE_RATES = intArrayOf(8000, 11025, 16000, 22050, 44100, 48000)
    }
}
