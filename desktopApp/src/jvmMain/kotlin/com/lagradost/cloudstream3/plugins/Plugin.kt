package com.lagradost.cloudstream3.plugins

import android.content.Context
import android.content.res.Resources
import android.util.Log
import com.lagradost.cloudstream3.actions.VideoClickAction
import com.lagradost.cloudstream3.actions.VideoClickActionHolder

abstract class Plugin : BasePlugin() {
    /**
     * Called when your Plugin is loaded
     * @param context Context
     */
    open fun load(context: Context) {
        // If not overridden by an extension then try the cross-platform load()
        load()
    }

    /**
     * Used to register VideoClickAction instances
     * @param element VideoClickAction you want to register
     */
    fun registerVideoClickAction(element: VideoClickAction) {
        Log.i(PLUGIN_TAG, "Adding ${element.name} VideoClickAction")
        element.sourcePlugin = this.filename
        VideoClickActionHolder.allVideoClickActions.add(element)
    }

    /**
     * Resources if specified requiresResources in gradle
     */
    var resources: Resources? = null

    /**
     * Optional settings action
     */
    var openSettings: ((context: Context) -> Unit)? = null
}
