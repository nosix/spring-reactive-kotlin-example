package com.example.api

import com.example.WebServiceFactory
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CustomerServiceTests {

    private val factory = WebServiceFactory("http://localhost:8080/")
    private val customerService = factory.create<CustomerService>()

    @BeforeEach
    fun setUp() {
        factory.reset()
    }

    private suspend fun authenticate() {
        factory.authenticate {
            factory.create<AuthenticationService>()
                .login(Credentials("user", "user_password"))
        }
    }

    @Test
    fun getAllCustomer(): Unit = runBlocking {
        authenticate()
        val customers = customerService.getAllCustomer()
        println(customers)
    }

    @Test
    fun getCustomer(): Unit = runBlocking {
        authenticate()
        val customer = customerService.getCustomer(1)
        println(customer)
    }

    @Test
    fun getCustomerByLastName(): Unit = runBlocking {
        authenticate()
        val customers = customerService.getCustomerByLastName("Last")
        println(customers)
    }

    @Test
    fun postCustomer(): Unit = runBlocking {
        authenticate()
        val customer = customerService.postCustomer(Customer("First", "Last"))
        println(customer)
    }
}