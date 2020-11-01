package com.example.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthenticationService {
    @POST("/login")
    suspend fun login(@Body credentials: Credentials): Response<Unit>
}