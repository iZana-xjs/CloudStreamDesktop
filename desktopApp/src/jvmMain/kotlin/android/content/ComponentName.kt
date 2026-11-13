package android.content

/** Minimal Android compatibility value used by some CloudStream extension bytecode. */
data class ComponentName(val packageName: String, val className: String) {
    constructor(context: Context, clazz: Class<*>) : this(context.getPackageName(), clazz.name)

    fun flattenToString(): String = "$packageName/$className"

    companion object {
        @JvmStatic
        fun unflattenFromString(value: String?): ComponentName? {
            if (value.isNullOrBlank()) return null
            val split = value.indexOf('/')
            if (split <= 0 || split == value.lastIndex) return null
            return ComponentName(value.substring(0, split), value.substring(split + 1))
        }
    }
}
