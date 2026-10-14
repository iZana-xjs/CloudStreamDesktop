package android.os

open class Looper private constructor() {
    companion object {
        private val mainLooperInstance = Looper()
        @JvmStatic
        fun getMainLooper(): Looper = mainLooperInstance
    }
}

open class Handler(looper: Looper? = null) {
    open fun post(r: Runnable): Boolean {
        r.run()
        return true
    }

    open fun postDelayed(r: Runnable, delayMillis: Long): Boolean {
        Thread {
            try {
                Thread.sleep(delayMillis)
                r.run()
            } catch (_: InterruptedException) {}
        }.start()
        return true
    }
}
