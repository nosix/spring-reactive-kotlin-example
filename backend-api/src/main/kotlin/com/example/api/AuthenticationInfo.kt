package com.example.api

import kotlinx.serialization.Serializable

@Serializable
data class AuthenticationInfo(
    val mailAddress: String,
    val password: String
)