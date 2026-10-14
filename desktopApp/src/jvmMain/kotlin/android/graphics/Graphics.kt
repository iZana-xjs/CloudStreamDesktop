package android.graphics

import android.graphics.drawable.Drawable

open class Bitmap

object Color {
    const val BLACK = -0x1000000
    const val DKGRAY = -0xbbbbbc
    const val GRAY = -0x777778
    const val LTGRAY = -0x333334
    const val WHITE = -0x1
    const val RED = -0x10000
    const val GREEN = -0xff0100
    const val BLUE = -0xffff01
    const val YELLOW = -0x100
    const val CYAN = -0xff0001
    const val MAGENTA = -0xff01
    const val TRANSPARENT = 0

    @JvmStatic
    fun parseColor(colorString: String): Int {
        if (colorString.startsWith("#")) {
            val color = colorString.substring(1).toLong(16)
            return if (colorString.length == 7) {
                (color or 0x00000000ff000000L).toInt()
            } else {
                color.toInt()
            }
        }
        return 0
    }
}
