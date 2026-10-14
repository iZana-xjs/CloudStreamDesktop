package android.app

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.View

open class Activity : Context() {
    open fun onCreate(savedInstanceState: Bundle?) {}
    open fun onDestroy() {}
    open fun finish() {}
    open fun setContentView(view: View) {}
    open fun setContentView(layoutResID: Int) {}
}

open class Application : Context()

open class Dialog(val context: Context) : DialogInterface {
    open fun show() {}
    open fun dismiss() {}
    open fun cancel() {}
    override fun cancelDialog() {}
    override fun dismissDialog() {}
}
