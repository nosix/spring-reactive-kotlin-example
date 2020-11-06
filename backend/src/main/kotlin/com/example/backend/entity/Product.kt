package com.example.backend.entity

import org.springframework.data.annotation.Id

data class Product(
    @Id val id: Long? = null,
    val name: String,
    val price: Int
)