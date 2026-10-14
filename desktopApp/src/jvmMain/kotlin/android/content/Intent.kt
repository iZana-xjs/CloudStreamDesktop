package android.content

import android.net.Uri

interface DialogInterface {
    fun cancelDialog() {}
    fun dismissDialog() {}

    fun interface OnClickListener {
        fun onClick(dialog: DialogInterface?, which: Int)
    }

    fun interface OnDismissListener {
        fun onDismiss(dialog: DialogInterface?)
    }

    fun interface OnCancelListener {
        fun onCancel(dialog: DialogInterface?)
    }

    companion object {
        const val BUTTON_POSITIVE = -1
        const val BUTTON_NEGATIVE = -2
        const val BUTTON_NEUTRAL = -3
    }
}

open class Intent {
    var action: String? = null
    var data: Uri? = null

    constructor()
    constructor(action: String) {
        this.action = action
    }
    constructor(action: String, uri: Uri) {
        this.action = action
        this.data = uri
    }

    open fun putExtra(name: String, value: String?): Intent = this
    open fun putExtra(name: String, value: Boolean): Intent = this
    open fun putExtra(name: String, value: Int): Intent = this
    open fun putExtra(name: String, value: Long): Intent = this
    open fun getStringExtra(name: String): String? = null
    open fun getBooleanExtra(name: String, defaultValue: Boolean): Boolean = defaultValue
    open fun getIntExtra(name: String, defaultValue: Int): Int = defaultValue

    companion object {
        const val ACTION_VIEW = "android.intent.action.VIEW"
    }
}
