package androidx.fragment.app

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

open class Fragment {
    open var activity: android.app.Activity? = null
    open var context: Context? = null

    open fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = null

    open fun onViewCreated(view: View, savedInstanceState: Bundle?) {}
    open fun onDestroyView() {}
    open fun onDestroy() {}
}

open class DialogFragment : Fragment(), DialogInterface.OnDismissListener, DialogInterface.OnCancelListener {
    open var dialog: Dialog? = null

    open fun onCreateDialog(savedInstanceState: Bundle?): Dialog = Dialog(context ?: Context())
    open fun dismiss() {}
    open fun dismissAllowingStateLoss() {}

    open fun show(manager: FragmentManager, tag: String?) {}

    override fun onDismiss(dialog: DialogInterface?) {}
    override fun onCancel(dialog: DialogInterface?) {}
}

open class FragmentManager {
    open fun beginTransaction(): FragmentTransaction = FragmentTransaction()
}

open class FragmentTransaction {
    open fun add(fragment: Fragment, tag: String?): FragmentTransaction = this
    open fun remove(fragment: Fragment): FragmentTransaction = this
    open fun commit(): Int = 0
    open fun commitAllowingStateLoss(): Int = 0
}
