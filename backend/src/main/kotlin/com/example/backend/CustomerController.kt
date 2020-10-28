package com.example.backend

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import org.springframework.data.repository.query.Param
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
//@RequestMapping("customers")
class CustomerController(
    private val repository: CustomerRepository
) {
    @GetMapping("customers")
    fun getAllCustomer(): Flow<Customer> {
        return repository.findAll()
    }

    @GetMapping("customer")
    suspend fun getCustomer(
        @Param("id") id: Long
    ): Customer? {
        return repository.findById(id)
    }

    @GetMapping("customer/name")
    suspend fun getCustomerByLastName(
        @Param("lastName") lastName: String
    ): List<Customer> {
        return repository.findByLastName(lastName).toList()
    }

    @PostMapping("customer")
    suspend fun postCustomer(
        customer: Customer
    ): Customer {
        return repository.save(customer)
    }
}