package com.example.api

import kotlinx.serialization.Serializable

@Serializable
data class Credentials(
    val mailAddress: String,
    val password: String
)