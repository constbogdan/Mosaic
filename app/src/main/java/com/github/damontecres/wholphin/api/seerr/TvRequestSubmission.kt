package com.github.damontecres.wholphin.api.seerr

import com.github.damontecres.wholphin.api.seerr.infrastructure.ApiClient
import com.github.damontecres.wholphin.api.seerr.infrastructure.ClientError
import com.github.damontecres.wholphin.api.seerr.infrastructure.ClientException
import com.github.damontecres.wholphin.api.seerr.infrastructure.RequestConfig
import com.github.damontecres.wholphin.api.seerr.infrastructure.Serializer
import com.github.damontecres.wholphin.api.seerr.infrastructure.ServerError
import com.github.damontecres.wholphin.api.seerr.infrastructure.ServerException
import com.github.damontecres.wholphin.api.seerr.infrastructure.Success
import com.github.damontecres.wholphin.api.seerr.model.MediaRequest
import com.github.damontecres.wholphin.api.seerr.model.RequestPostRequest
import com.github.damontecres.wholphin.api.seerr.model.RequestRequestIdPutRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.Call
import java.io.IOException

sealed interface TvRequestOutcome {
    data class Submitted(
        val request: MediaRequest,
    ) : TvRequestOutcome

    data object NoNewRequest : TvRequestOutcome
}

class TvRequestResponseException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

/** Reuses generated request configs and transport, but decodes TV's distinct 202 body first. */
internal class TvRequestSubmission(
    baseUrl: String,
    client: Call.Factory,
    private val requests: RequestApi,
) : ApiClient(baseUrl, client) {
    suspend fun create(body: RequestPostRequest): TvRequestOutcome {
        require(body.mediaType == RequestPostRequest.MediaType.TV)
        return submit(requests.requestPostRequestConfig(body), 201)
    }

    suspend fun update(
        id: String,
        body: RequestRequestIdPutRequest,
    ): TvRequestOutcome {
        require(body.mediaType == RequestRequestIdPutRequest.MediaType.TV)
        return submit(requests.requestRequestIdPutRequestConfig(id, body), 200)
    }

    private suspend inline fun <reified T> submit(
        config: RequestConfig<T>,
        expectedStatus: Int,
    ): TvRequestOutcome =
        withContext(Dispatchers.IO) {
            val response =
                try {
                    request<T, JsonObject>(config)
                } catch (error: SerializationException) {
                    throw TvRequestResponseException("Malformed TV request response", error)
                }
            currentCoroutineContext().ensureActive()
            when (response) {
                is ClientError -> {
                    throw ClientException("TV request failed: ${response.statusCode}", response.statusCode, response)
                }

                is ServerError -> {
                    throw ServerException("TV request failed: ${response.statusCode}", response.statusCode, response)
                }

                is Success -> {
                    val body = response.data ?: throw TvRequestResponseException("Empty TV request response")
                    if (response.statusCode == 202) {
                        // This means no request was created, not asynchronous download acceptance.
                        if (body["message"] != JsonPrimitive("No seasons available to request") || "id" in body || "status" in body) {
                            throw TvRequestResponseException("Unrecognized TV request acknowledgement")
                        }
                        TvRequestOutcome.NoNewRequest
                    } else {
                        if (response.statusCode != expectedStatus) {
                            throw TvRequestResponseException("Unexpected TV request status: ${response.statusCode}")
                        }
                        val submitted =
                            try {
                                Serializer.kotlinxSerializationJson.decodeFromJsonElement<MediaRequest>(body)
                            } catch (error: SerializationException) {
                                throw TvRequestResponseException("Malformed TV request object", error)
                            }
                        if (submitted.id <= 0 || submitted.status !in 1..5 || (submitted.type != null && submitted.type != "tv")) {
                            throw TvRequestResponseException("Invalid TV request identity or status")
                        }
                        TvRequestOutcome.Submitted(submitted)
                    }
                }

                else -> {
                    throw TvRequestResponseException("Unexpected TV request response: ${response.statusCode}")
                }
            }
        }
}
