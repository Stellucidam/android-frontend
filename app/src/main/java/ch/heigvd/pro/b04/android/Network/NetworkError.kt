package ch.heigvd.pro.b04.android.Network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

import retrofit2.Response

sealed class NetworkError {
    object TokenNotValid : NetworkError()
    object NotFound : NetworkError()
}

const val DELAY : Long = 1000

private fun <T> errorFrom(response: Response<T>): NetworkError? {
    return when (response.code()) {
        404 -> NetworkError.NotFound
        else -> null
    }
}

fun <T> Flow<Response<T>>.keepError() : Flow<NetworkError> =
    filter { it.isSuccessful.not() }
        .map { errorFrom(it) }
        .filterNotNull()

fun <T:Any> Flow<Response<T>>.keepBody() : Flow<T> =
    filter { it.isSuccessful }
        .map { it.body() }
        .filterNotNull()

