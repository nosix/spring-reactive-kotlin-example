package com.example.api

import kotlinx.serialization.Serializable

// TODO change to Credential
@Serializable
data class AuthenticationInfo(
    val mailAddress: String,
    val password: String
)