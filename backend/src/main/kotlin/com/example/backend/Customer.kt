package com.example.backend

import kotlinx.serialization.Serializable
import org.springframework.data.annotation.Id

@Serializable
data class Customer(
        @Id
        val id: Long? = null,
        val firstName: String,
        val lastName: String
)