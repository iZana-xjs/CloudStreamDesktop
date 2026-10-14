package android.widget

import android.content.Context
import com.lagradost.cloudstream4.core.NotificationService

class Toast(val context: Context?) {
    companion object {
        const val LENGTH_SHORT = 0
        const val LENGTH_LONG = 1

        @JvmStatic
        fun makeText(context: Context?, text: CharSequence?, duration: Int): Toast {
            if (!text.isNullOrBlank()) {
                NotificationService.info(text.toString())
            }
            return Toast(context)
        }
    }

    fun show() {
        // NotificationService already displays toast
    }
}
