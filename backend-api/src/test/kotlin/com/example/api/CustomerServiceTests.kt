package com.example.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType
import org.junit.jupiter.api.Test
import retrofit2.Retrofit

@OptIn(ExperimentalSerializationApi::class)
class CustomerServiceTests {

    private val retrofit = Retrofit.Builder()
        .baseUrl("http://localhost:8080/")
        .addConverterFactory(Json.asConverterFactory(MediaType.get("application/json")))
        .build()

    private val service = retrofit.create(CustomerService::class.java)

    @Test
    fun getAllCustomer(): Unit = runBlocking {
        val customers = service.getAllCustomer()
        println(customers)
    }

    @Test
    fun getCustomer(): Unit = runBlocking {
        val customer = service.getCustomer(1)
        println(customer)
    }

    @Test
    fun getCustomerByLastName(): Unit = runBlocking {
        val customers = service.getCustomerByLastName("Last")
        println(customers)
    }

    @Test
    fun postCustomer(): Unit = runBlocking {
        val customer = service.postCustomer(Customer("First", "Last"))
        println(customer)
    }
}