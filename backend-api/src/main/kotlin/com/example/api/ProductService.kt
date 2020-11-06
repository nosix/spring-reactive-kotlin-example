package com.example.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ProductService {
    @GET("products/{id}")
    suspend fun getProduct(@Path("id") id: Long): Optional<Product>

    @POST("products")
    suspend fun postProduct(@Body product: Product): Product

    @GET("products/search")
    suspend fun getProductByName(@Query("name") name: String? = null): List<Product>
}