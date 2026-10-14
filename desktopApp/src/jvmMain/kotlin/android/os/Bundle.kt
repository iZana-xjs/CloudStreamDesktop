package android.os

open class Bundle {
    private val map = mutableMapOf<String, Any?>()

    open fun putString(key: String, value: String?) { map[key] = value }
    open fun putInt(key: String, value: Int) { map[key] = value }
    open fun putBoolean(key: String, value: Boolean) { map[key] = value }
    open fun putLong(key: String, value: Long) { map[key] = value }

    open fun getString(key: String): String? = map[key] as? String
    open fun getString(key: String, defaultValue: String): String = map[key] as? String ?: defaultValue
    open fun getInt(key: String, defaultValue: Int = 0): Int = (map[key] as? Number)?.toInt() ?: defaultValue
    open fun getBoolean(key: String, defaultValue: Boolean = false): Boolean = map[key] as? Boolean ?: defaultValue
    open fun getLong(key: String, defaultValue: Long = 0L): Long = (map[key] as? Number)?.toLong() ?: defaultValue
    open fun containsKey(key: String): Boolean = map.containsKey(key)
}
