package com.example.api

import kotlinx.serialization.Serializable

@Serializable
data class Product(val id: Long?, val name: String, val price: Int)