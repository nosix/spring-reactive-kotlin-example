package com.example.backend.repository

import com.example.backend.entity.Product
import kotlinx.coroutines.flow.Flow
import org.springframework.data.repository.kotlin.CoroutineCrudRepository

interface ProductRepository : CoroutineCrudRepository<Product, Long> {
    suspend fun findByName(name: String): Flow<Product>
}