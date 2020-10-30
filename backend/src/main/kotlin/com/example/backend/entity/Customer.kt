package com.example.backend.entity

import org.springframework.data.annotation.Id

data class Customer(
        @Id val id: Long? = null,
        val firstName: String,
        val lastName: String
)