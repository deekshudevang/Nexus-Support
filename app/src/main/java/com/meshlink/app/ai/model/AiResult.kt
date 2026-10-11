package com.meshlink.app.ai.model

/**
 * Sealed result contract for all AI operations. Swallowed exceptions are strictly prohibited.
 */
sealed class AiResult<out T> {
    data class Success<out T>(val data: T) : AiResult<T>()
    data class Failure(val error: AiError) : AiResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Failure -> null
    }

    inline fun <R> map(transform: (T) -> R): AiResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Failure -> Failure(error)
    }

    inline fun onSuccess(action: (T) -> Unit): AiResult<T> {
        if (this is Success) action(data)
        return this
    }

    inline fun onFailure(action: (AiError) -> Unit): AiResult<T> {
        if (this is Failure) action(error)
        return this
    }
}
