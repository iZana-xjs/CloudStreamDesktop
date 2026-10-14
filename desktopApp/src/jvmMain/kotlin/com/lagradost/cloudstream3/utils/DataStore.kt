package com.lagradost.cloudstream3.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJsonLiteral
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

@OptIn(com.lagradost.cloudstream3.InternalAPI::class)
const val DOWNLOAD_HEADER_CACHE = "download_header_cache"
const val DOWNLOAD_HEADER_CACHE_BACKUP = "BACKUP_download_header_cache"
const val DOWNLOAD_EPISODE_CACHE = "download_episode_cache"
const val DOWNLOAD_EPISODE_CACHE_BACKUP = "BACKUP_download_episode_cache"
const val VIDEO_PLAYER_BRIGHTNESS = "video_player_alpha_key"
const val USER_SELECTED_HOMEPAGE_API = "home_api_used"
const val USER_PROVIDER_API = "user_custom_sites"
const val PREFERENCES_NAME = "rebuild_preference"

class PreferenceDelegate<T : Any>(
    val key: String, val default: T
) {
    private val klass: KClass<out T> = default::class
    private var cache: T? = null

    operator fun getValue(self: Any?, property: KProperty<*>) =
        cache ?: default

    operator fun setValue(
        self: Any?,
        property: KProperty<*>,
        t: T?
    ) {
        cache = t
    }
}

data class Editor(
    val editor: SharedPreferences.Editor
) {
    fun <T> setKeyRaw(path: String, value: T) {
        when (value) {
            is Boolean -> editor.putBoolean(path, value)
            is Int -> editor.putInt(path, value)
            is String -> editor.putString(path, value)
            is Float -> editor.putFloat(path, value)
            is Long -> editor.putLong(path, value)
        }
    }

    fun apply() {
        editor.apply()
    }
}

@OptIn(com.lagradost.cloudstream3.InternalAPI::class)
object DataStore {
    val mapper = com.lagradost.cloudstream3.mapper

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFERENCES_NAME, 0)
    }

    fun Context.getSharedPrefs(): SharedPreferences {
        return getPreferences(this)
    }

    fun getFolderName(folder: String, path: String): String {
        return "${folder}/${path}"
    }

    fun editor(context: Context, isEditingAppSettings: Boolean = false): Editor {
        val editor: SharedPreferences.Editor =
            if (isEditingAppSettings) context.getDefaultSharedPrefs().edit() else context.getSharedPrefs().edit()
        return Editor(editor)
    }

    fun Context.getDefaultSharedPrefs(): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(this)
    }

    fun Context.getKeys(folder: String): List<String> {
        val fixedFolder = folder.trimEnd('/') + "/"
        return this.getSharedPrefs().getAll().keys.filter { it.startsWith(fixedFolder) }
    }

    fun Context.removeKey(folder: String, path: String) {
        removeKey(getFolderName(folder, path))
    }

    fun Context.containsKey(folder: String, path: String): Boolean {
        return containsKey(getFolderName(folder, path))
    }

    fun Context.containsKey(path: String): Boolean {
        val prefs = getSharedPrefs()
        return prefs.contains(path)
    }

    fun Context.removeKey(path: String) {
        val prefs = getSharedPrefs()
        if (prefs.contains(path)) {
            prefs.edit {
                remove(path)
            }
        }
    }

    fun Context.removeKeys(folder: String): Int {
        val keys = getKeys("$folder/")
        getSharedPrefs().edit {
            keys.forEach { value ->
                remove(value)
            }
        }
        return keys.size
    }

    fun <T> Context.setKey(path: String, value: T) {
        try {
            getSharedPrefs().edit {
                putString(path, value?.toJsonLiteral())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun <T : Any> Context.getKey(path: String, valueType: Class<T>): T? {
        return try {
            val json: String = getSharedPrefs().getString(path, null) ?: return null
            parseJson(json, valueType.kotlin)
        } catch (_: Exception) {
            null
        }
    }

    fun <T> Context.setKey(folder: String, path: String, value: T) {
        setKey(getFolderName(folder, path), value)
    }

    inline fun <reified T : Any> Context.getKey(path: String, defVal: T?): T? {
        return try {
            val json: String = getSharedPrefs().getString(path, null) ?: return defVal
            parseJson<T>(json)
        } catch (_: Exception) {
            null
        }
    }

    inline fun <reified T : Any> Context.getKey(path: String): T? {
        return getKey(path, null)
    }

    inline fun <reified T : Any> Context.getKey(folder: String, path: String): T? {
        return getKey(getFolderName(folder, path), null)
    }

    inline fun <reified T : Any> Context.getKey(folder: String, path: String, defVal: T?): T? {
        return getKey(getFolderName(folder, path), defVal) ?: defVal
    }
}

object DataStoreHelper {
    var currentAccount: String = "default"
}
