package com.example.api

import kotlinx.serialization.Serializable

@Serializable
data class Customer(
    val id: Long? = null,
    val firstName: String,
    val lastName: String
) {
    constructor(firstName: String, lastName: String) : this(null, firstName, lastName)
}