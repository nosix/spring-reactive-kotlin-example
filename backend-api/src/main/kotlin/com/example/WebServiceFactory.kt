package com.example

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Response
import retrofit2.Retrofit
import java.lang.reflect.Type
import kotlin.reflect.KClass

class WebServiceFactory(baseUrl: String) {

    private val securityContext = object {
        var authHeader: String? = null
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = securityContext.authHeader?.let { value ->
                chain.request().newBuilder()
                    .addHeader("Authorization", value)
                    .build()
            } ?: chain.request()
            chain.proceed(request)
        }
        .build()

    @OptIn(ExperimentalSerializationApi::class)
    private val retrofit = Retrofit.Builder()
        .addConverterFactory(NullOnEmptyConverterFactory)
        .addConverterFactory(Json.asConverterFactory(MediaType.get("application/json")))
        .baseUrl(baseUrl)
        .client(client)
        .build()

    inline fun <reified T : Any> create(): T = create(T::class)

    fun <T : Any> create(service: KClass<T>): T {
        return retrofit.create(service.java)
    }

    suspend fun authenticate(action: suspend () -> Response<Unit>) {
        val authHeader: String? = action().run {
            if (isSuccessful) headers().get("Authorization") else null
        }
        securityContext.authHeader = authHeader
    }

    fun reset() {
        securityContext.authHeader = null
    }

    private object NullOnEmptyConverterFactory : Converter.Factory() {
        override fun responseBodyConverter(
            type: Type,
            annotations: Array<out Annotation>,
            retrofit: Retrofit
        ): Converter<ResponseBody, Any?>? {
            val delegate = retrofit.nextResponseBodyConverter<Any?>(this, type, annotations)
            return Converter<ResponseBody, Any?> {
                if (it.contentLength() == 0L) null else delegate.convert(it)
            }
        }
    }
}