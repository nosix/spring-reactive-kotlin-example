package com.example.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface CustomerService {
    @GET("/customers")
    suspend fun getAllCustomer(): List<Customer>

    @GET("/customer")
    suspend fun getCustomer(@Query("id") id: Long): Customer

    @GET("/customer/name")
    suspend fun getCustomerByLastName(
        @Query("lastName") lastName: String
    ): List<Customer>

    @POST("/customer")
    suspend fun postCustomer(
        @Body customer: Customer
    ): Customer
}