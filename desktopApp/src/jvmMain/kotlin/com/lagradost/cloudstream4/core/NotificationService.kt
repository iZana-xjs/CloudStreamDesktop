package com.lagradost.cloudstream4.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed class ToastType {
    object Info : ToastType()
    object Success : ToastType()
    object Warning : ToastType()
    object Error : ToastType()
}

data class ToastMessage(
    val title: String,
    val description: String? = null,
    val type: ToastType = ToastType.Info,
    val durationMs: Long = 4000L
)

object NotificationService {
    private val _notifications = MutableSharedFlow<ToastMessage>(extraBufferCapacity = 64)
    val notifications: SharedFlow<ToastMessage> = _notifications.asSharedFlow()

    fun show(title: String, description: String? = null, type: ToastType = ToastType.Info) {
        _notifications.tryEmit(ToastMessage(title, description, type))
    }

    fun success(title: String, description: String? = null) {
        show(title, description, ToastType.Success)
    }

    fun error(title: String, description: String? = null) {
        show(title, description, ToastType.Error)
    }

    fun info(title: String, description: String? = null) {
        show(title, description, ToastType.Info)
    }

    fun warning(title: String, description: String? = null) {
        show(title, description, ToastType.Warning)
    }
}
