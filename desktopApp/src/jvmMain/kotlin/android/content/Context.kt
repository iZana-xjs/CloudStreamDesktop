package android.content

import com.lagradost.cloudstream4.core.AppPaths
import java.io.File

open class Context {
    open fun getPackageName(): String = "com.lagradost.cloudstream3"
    open fun getString(resId: Int): String = ""
    open fun getFilesDir(): File = AppPaths.dataDir
    open fun getCacheDir(): File = AppPaths.cacheDir
    open fun getSharedPreferences(name: String, mode: Int): SharedPreferences = DesktopSharedPreferences(name)
}

interface SharedPreferences {
    fun getString(key: String, defValue: String?): String?
    fun getInt(key: String, defValue: Int): Int
    fun getBoolean(key: String, defValue: Boolean): Boolean
    fun getLong(key: String, defValue: Long): Long
    fun getFloat(key: String, defValue: Float): Float
    fun getAll(): Map<String, *>
    fun contains(key: String): Boolean
    fun edit(): Editor

    interface Editor {
        fun putString(key: String, value: String?): Editor
        fun putInt(key: String, value: Int): Editor
        fun putBoolean(key: String, value: Boolean): Editor
        fun putLong(key: String, value: Long): Editor
        fun putFloat(key: String, value: Float): Editor
        fun remove(key: String): Editor
        fun clear(): Editor
        fun apply()
        fun commit(): Boolean
    }
}

class DesktopSharedPreferences(val prefName: String) : SharedPreferences {
    companion object {
        private val globalStore = mutableMapOf<String, MutableMap<String, Any?>>()
    }

    private val map: MutableMap<String, Any?>
        get() = globalStore.getOrPut(prefName) { mutableMapOf() }

    override fun getString(key: String, defValue: String?): String? = map[key] as? String ?: defValue
    override fun getInt(key: String, defValue: Int): Int = (map[key] as? Number)?.toInt() ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
    override fun getLong(key: String, defValue: Long): Long = (map[key] as? Number)?.toLong() ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = (map[key] as? Number)?.toFloat() ?: defValue
    override fun getAll(): Map<String, *> = HashMap(map)
    override fun contains(key: String): Boolean = map.containsKey(key)
    override fun edit(): SharedPreferences.Editor = EditorImpl(map)

    class EditorImpl(private val target: MutableMap<String, Any?>) : SharedPreferences.Editor {
        override fun putString(key: String, value: String?): SharedPreferences.Editor { target[key] = value; return this }
        override fun putInt(key: String, value: Int): SharedPreferences.Editor { target[key] = value; return this }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor { target[key] = value; return this }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor { target[key] = value; return this }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor { target[key] = value; return this }
        override fun remove(key: String): SharedPreferences.Editor { target.remove(key); return this }
        override fun clear(): SharedPreferences.Editor { target.clear(); return this }
        override fun apply() {}
        override fun commit(): Boolean = true
    }
}
