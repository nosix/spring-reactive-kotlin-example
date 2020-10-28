package com.example.backend.repository

import org.springframework.data.annotation.Id

data class Customer(
        @Id val id: Long? = null,
        val firstName: String,
        val lastName: String
)