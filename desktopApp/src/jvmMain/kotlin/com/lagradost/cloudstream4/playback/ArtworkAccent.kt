package com.lagradost.cloudstream4.playback

import androidx.compose.ui.graphics.Color
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import java.util.concurrent.ConcurrentHashMap

/**
 * Derives a small, restrained accent palette from the currently playing title's
 * artwork. Used ONLY for player UI accents (seek bar, active controls,
 * selection highlights) — never to tint the video image.
 *
 * - Palette is cached by artwork URL; never sampled per frame.
 * - Falls back to CloudStream red when artwork is missing/loading/unsuitable.
 * - Byte loading and Skia sampling are injectable for fast unit tests; the
 *   real path runs off the UI thread.
 */
object PlayerArtworkAccent {
    const val FALLBACK_HEX: Long = 0xFFE50914L // CloudStream red

    private val cache = ConcurrentHashMap<String, Long>()

    fun fallback(): Long = FALLBACK_HEX

    fun cached(artworkUrl: String?): Long? {
        if (artworkUrl.isNullOrBlank()) return null
        return cache[artworkUrl]
    }

    fun put(artworkUrl: String?, accent: Long) {
        if (!artworkUrl.isNullOrBlank()) cache[artworkUrl] = accent
    }

    fun clear() = cache.clear()

    /** Loads artwork bytes (injectable; null/blank URL yields null). */
    fun interface ArtworkBytesLoader {
        suspend fun load(artworkUrl: String): ByteArray?
    }

    /**
     * Resolves the accent for a title: cache first, then sample, then fallback.
     */
    suspend fun accentFor(
        artworkUrl: String?,
        loader: ArtworkBytesLoader
    ): Long {
        if (!artworkUrl.isNullOrBlank()) {
            cache[artworkUrl]?.let { return it }
        }
        val sampled = if (!artworkUrl.isNullOrBlank()) runCatching { sampleAccent(loader.load(artworkUrl)) }.getOrNull() else null
        val accent = sampled ?: FALLBACK_HEX
        put(artworkUrl, accent)
        return accent
    }

    /** Decodes bytes via Skia and averages a coarse grid. Pure-ish (no UI). */
    fun sampleAccent(bytes: ByteArray?): Long? {
        if (bytes == null || bytes.isEmpty()) return null
        val image = runCatching { Image.makeFromEncoded(bytes) }.getOrNull() ?: return null
        val info = image.imageInfo
        if (info.width <= 0 || info.height <= 0) return null
        val bitmap = Bitmap().apply { allocPixels(info) }
        if (!image.readPixels(bitmap)) return null

        val stepX = (bitmap.width / 8).coerceAtLeast(1)
        val stepY = (bitmap.height / 8).coerceAtLeast(1)
        var rSum = 0L; var gSum = 0L; var bSum = 0L; var count = 0L
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val c = bitmap.getColor(x, y)
                rSum += ((c shr 16) and 0xFF).toLong()
                gSum += ((c shr 8) and 0xFF).toLong()
                bSum += (c and 0xFF).toLong()
                count++
                x += stepX
            }
            y += stepY
        }
        if (count == 0L) return null
        return usableMidTone((rSum / count).toInt(), (gSum / count).toInt(), (bSum / count).toInt())
    }

    /**
     * Nudges a raw average toward a readable mid-tone: keeps hue, pulls
     * extreme lightness/darkness toward the middle so white text and icons
     * keep contrast. Pure, for unit tests.
     */
    fun usableMidTone(r: Int, g: Int, b: Int): Long {
        fun mid(v: Int): Int = (v.coerceIn(0, 255) + (128 - v.coerceIn(0, 255)) * 0.35f).toInt().coerceIn(0, 255)
        val rr = mid(r); val gg = mid(g); val bb = mid(b)
        return (0xFFL shl 24) or (rr.toLong() shl 16) or (gg.toLong() shl 8) or bb.toLong()
    }

    // `Color(Int)` expects a packed ARGB value. Masking off the high byte
    // zeroed alpha, so the sampled accent was fully transparent in Compose.
    fun toColor(accentLong: Long): Color = Color(accentLong.toInt())
}
