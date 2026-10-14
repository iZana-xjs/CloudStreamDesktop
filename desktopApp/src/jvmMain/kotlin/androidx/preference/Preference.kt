package androidx.preference

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.fragment.app.Fragment

open class PreferenceFragmentCompat : Fragment() {
    open fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {}
    open fun setPreferencesFromResource(preferencesResId: Int, key: String?) {}
}

object PreferenceManager {
    fun getDefaultSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences("default_preferences", 0)
    }
}
