package ru.vogu.smartfinance.core.common

/**
 * Универсальная обёртка результата операции — используется UseCase-ами для передачи
 * состояния "успех / ошибка / загрузка" в ViewModel без утечки деталей реализации
 * (например, конкретного типа исключения сетевого клиента) в presentation-слой.
 */
sealed class Resource<out T> {
    data class Success<out T>(val data: T) : Resource<T>()
    data class Error(val message: String, val cause: Throwable? = null) : Resource<Nothing>()
    data object Loading : Resource<Nothing>()

    inline fun onSuccess(action: (T) -> Unit): Resource<T> {
        if (this is Success) action(data)
        return this
    }

    inline fun onError(action: (String, Throwable?) -> Unit): Resource<T> {
        if (this is Error) action(message, cause)
        return this
    }

    fun <R> map(transform: (T) -> R): Resource<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> Error(message, cause)
        is Loading -> Loading
    }
}
