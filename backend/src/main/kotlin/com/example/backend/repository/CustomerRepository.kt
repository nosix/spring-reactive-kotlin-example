package com.example.backend.repository

import com.example.backend.entity.Customer
import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository

interface CustomerRepository : CoroutineCrudRepository<Customer, Long> {
    @Query("SELECT * FROM customer WHERE last_name = :lastName")
    fun findByLastName(lastName: String): Flow<Customer>
}