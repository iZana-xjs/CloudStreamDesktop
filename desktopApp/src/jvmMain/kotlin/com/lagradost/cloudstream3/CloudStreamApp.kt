package com.lagradost.cloudstream3

import android.app.Application
import android.content.Context
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.getKeys
import com.lagradost.cloudstream3.utils.DataStore.removeKey
import com.lagradost.cloudstream3.utils.DataStore.removeKeys
import com.lagradost.cloudstream3.utils.DataStore.setKey

open class CloudStreamApp : Application() {
    companion object {
        var context: Context? = Context()

        fun <T : Any> getKeyClass(path: String, valueType: Class<T>): T? {
            return context?.getKey(path, valueType)
        }

        fun <T : Any> setKeyClass(path: String, value: T) {
            context?.setKey(path, value)
        }

        fun removeKeys(folder: String): Int? {
            return context?.removeKeys(folder)
        }

        fun <T> setKey(path: String, value: T) {
            context?.setKey(path, value)
            // MegaProvider and other CloudStream plugins persist discovered repos
            // through this API. The desktop marketplace uses DesktopStorage, so
            // mirror the repository array into its persistent repository list.
            if (path == "REPOSITORIES_KEY" && value is Array<*>) {
                val repositories = value.mapNotNull { repository ->
                    if (repository == null) return@mapNotNull null
                    try {
                        val type = repository.javaClass
                        fun stringProperty(name: String): String? = runCatching {
                            type.getMethod("get${name.replaceFirstChar { it.uppercase() }}")
                                .invoke(repository) as? String
                        }.getOrNull()
                        val name = stringProperty("name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        val url = stringProperty("url")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        com.lagradost.cloudstream4.core.StoredRepository(
                            name = name,
                            url = url,
                            iconUrl = stringProperty("iconUrl")
                        )
                    } catch (_: Throwable) {
                        null
                    }
                }
                if (repositories.isNotEmpty()) {
                    com.lagradost.cloudstream4.core.DesktopStorage.mergePluginRepositories(repositories)
                    com.lagradost.cloudstream4.core.DesktopStorage.notifyRepositoriesChanged()
                }
            }
        }

        fun <T> setKey(folder: String, path: String, value: T) {
            context?.setKey(folder, path, value)
        }

        inline fun <reified T : Any> getKey(path: String, defVal: T?): T? {
            return context?.getKey(path, defVal)
        }

        inline fun <reified T : Any> getKey(path: String): T? {
            return context?.getKey(path)
        }

        inline fun <reified T : Any> getKey(folder: String, path: String): T? {
            return context?.getKey(folder, path)
        }

        inline fun <reified T : Any> getKey(folder: String, path: String, defVal: T?): T? {
            return context?.getKey(folder, path, defVal)
        }

        fun getKeys(folder: String): List<String>? {
            return context?.getKeys(folder)
        }

        fun removeKey(folder: String, path: String) {
            context?.removeKey(folder, path)
        }

        fun removeKey(path: String) {
            context?.removeKey(path)
        }
    }
}
