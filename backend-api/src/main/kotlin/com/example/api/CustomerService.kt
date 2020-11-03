package com.example.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface CustomerService {
    @GET("customers")
    suspend fun getAllCustomers(): List<Customer>

    @GET("customers/{id}")
    suspend fun getCustomer(@Path("id") id: Long): Customer?

    @GET("customers/search")
    suspend fun getCustomersByLastName(
        @Query("lastName") lastName: String
    ): List<Customer>

    @POST("customers")
    suspend fun postCustomer(
        @Body customer: Customer
    ): Customer
}