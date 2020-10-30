package com.example.api

import kotlinx.serialization.Serializable

@Serializable
data class Credential(
    val mailAddress: String,
    val password: String
)