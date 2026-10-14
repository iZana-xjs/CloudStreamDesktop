package androidx.appcompat.app

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.view.View

open class AppCompatActivity : Activity()

open class AlertDialog(context: Context) : Dialog(context) {
    open class Builder(val context: Context) {
        open fun setTitle(title: CharSequence?): Builder = this
        open fun setMessage(message: CharSequence?): Builder = this
        open fun setPositiveButton(text: CharSequence?, listener: DialogInterface.OnClickListener?): Builder = this
        open fun setNegativeButton(text: CharSequence?, listener: DialogInterface.OnClickListener?): Builder = this
        open fun setNeutralButton(text: CharSequence?, listener: DialogInterface.OnClickListener?): Builder = this
        open fun setView(view: View?): Builder = this
        open fun create(): AlertDialog = AlertDialog(context)
        open fun show(): AlertDialog = create().apply { show() }
    }
}
