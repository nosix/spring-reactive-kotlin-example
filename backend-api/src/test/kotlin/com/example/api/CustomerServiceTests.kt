package com.example.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Test
import retrofit2.Retrofit

@OptIn(ExperimentalSerializationApi::class)
class CustomerServiceTests {

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

    private val retrofit = Retrofit.Builder()
        .baseUrl("http://localhost:8080/")
        .addConverterFactory(Json.asConverterFactory(MediaType.get("application/json")))
        .client(client)
        .build()

    private val authService = retrofit.create(AuthenticationService::class.java)
    private val customerService = retrofit.create(CustomerService::class.java)

    @Test
    fun getAllCustomer(): Unit = runBlocking {
        val authHeader: String? = authService.login(AuthenticationInfo("user", "user_password")).run {
            if (isSuccessful) headers().get("Authorization") else null
        }
        println(authHeader)

        securityContext.authHeader = authHeader

        val customers = customerService.getAllCustomer()
        println(customers)
    }

    @Test
    fun getCustomer(): Unit = runBlocking {
        val customer = customerService.getCustomer(1)
        println(customer)
    }

    @Test
    fun getCustomerByLastName(): Unit = runBlocking {
        val customers = customerService.getCustomerByLastName("Last")
        println(customers)
    }

    @Test
    fun postCustomer(): Unit = runBlocking {
        val customer = customerService.postCustomer(Customer("First", "Last"))
        println(customer)
    }
}