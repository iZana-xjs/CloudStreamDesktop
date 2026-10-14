package android.webkit

import android.content.Context
import android.view.View

open class CookieManager {
    companion object {
        private val instance = CookieManager()
        @JvmStatic
        fun getInstance(): CookieManager = instance
    }

    private val cookieStore = mutableMapOf<String, String>()

    open fun getCookie(url: String?): String? {
        if (url == null) return null
        return cookieStore[url]
    }

    open fun setCookie(url: String?, value: String?) {
        if (url != null && value != null) {
            cookieStore[url] = value
        }
    }

    open fun setAcceptCookie(accept: Boolean) {}
    open fun acceptCookie(): Boolean = true
    open fun removeAllCookies(callback: Any? = null) {
        cookieStore.clear()
    }
    open fun flush() {}
}

open class WebView(context: Context? = null) : View(context) {
    open fun loadUrl(url: String) {}
    open fun getSettings(): WebSettings = WebSettings()
}

open class WebSettings {
    var userAgentString: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    var javaScriptEnabled: Boolean = true
    var domStorageEnabled: Boolean = true
}
