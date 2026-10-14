package android.widget

import android.content.Context
import android.view.View
import android.view.ViewGroup

open class TextView(context: Context? = null) : View(context) {
    var text: CharSequence? = ""
}

open class EditText(context: Context? = null) : TextView(context)
open class Button(context: Context? = null) : TextView(context)
open class ImageView(context: Context? = null) : View(context)
open class LinearLayout(context: Context? = null) : ViewGroup(context)
open class FrameLayout(context: Context? = null) : ViewGroup(context)
