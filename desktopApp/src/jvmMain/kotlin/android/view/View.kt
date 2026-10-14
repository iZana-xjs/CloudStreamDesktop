package android.view

import android.content.Context

open class View(val context: Context? = null) {
    var visibility: Int = 0
    open fun setOnClickListener(listener: OnClickListener?) {}

    fun interface OnClickListener {
        fun onClick(v: View)
    }

    companion object {
        const val VISIBLE = 0
        const val INVISIBLE = 4
        const val GONE = 8
    }
}

open class ViewGroup(context: Context? = null) : View(context) {
    open fun addView(child: View) {}
    open fun removeView(child: View) {}
}

open class LayoutInflater {
    open fun inflate(resource: Int, root: ViewGroup?, attachToRoot: Boolean = false): View = View()

    companion object {
        @JvmStatic
        fun from(context: Context): LayoutInflater = LayoutInflater()
    }
}
