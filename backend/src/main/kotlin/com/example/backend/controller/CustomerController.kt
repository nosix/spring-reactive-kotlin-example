package com.example.backend.controller

import com.example.api.Customer
import com.example.api.CustomerService
import com.example.backend.repository.CustomerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.springframework.data.repository.query.Param
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import com.example.backend.entity.Customer as CustomerEntity

@RestController
class CustomerController(
    private val repository: CustomerRepository
) : CustomerService {

    private fun CustomerEntity.asApi(): Customer = Customer(id, firstName, lastName)
    private suspend fun Flow<CustomerEntity>.asApi(): List<Customer> = map { it.asApi() }.toList()

    private fun Customer.asEntity(): CustomerEntity = CustomerEntity(id, firstName, lastName)

    @GetMapping("customers")
    override suspend fun getAllCustomer(): List<Customer> {
        return repository.findAll().asApi()
    }

    @GetMapping("customers/{id}")
    override suspend fun getCustomer(
        @PathVariable("id") id: Long
    ): Customer? {
        return repository.findById(id)?.asApi()
    }

    @GetMapping("customers/search")
    override suspend fun getCustomerByLastName(
        @Param("lastName") lastName: String
    ): List<Customer> {
        return repository.findByLastName(lastName).asApi()
    }

    @PostMapping("customers")
    override suspend fun postCustomer(
        @RequestBody customer: Customer
    ): Customer {
        return repository.save(customer.asEntity()).asApi()
    }
}