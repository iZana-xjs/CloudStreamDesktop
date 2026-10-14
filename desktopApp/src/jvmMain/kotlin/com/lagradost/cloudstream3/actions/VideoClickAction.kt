package com.lagradost.cloudstream3.actions

object VideoClickActionHolder {
    val allVideoClickActions = mutableListOf<VideoClickAction>()
}

abstract class VideoClickAction {
    open val name: Any? = null
    var sourcePlugin: String? = null
    open val isPlayer: Boolean = false
    open fun uniqueId(): String = this.javaClass.simpleName
}
