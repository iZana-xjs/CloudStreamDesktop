package android.content.res

open class Resources {
    open fun getString(id: Int): String = ""
    open fun getString(id: Int, vararg formatArgs: Any?): String = ""
}
