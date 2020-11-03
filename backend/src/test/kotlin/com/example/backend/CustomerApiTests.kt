package com.example.backend

import com.example.WebServiceFactory
import com.example.api.AuthenticationService
import com.example.api.Credentials
import com.example.api.Customer
import com.example.api.CustomerService
import com.example.backend.repository.CustomerRepository
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito
import org.mockito.Mockito.any
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.HttpStatus
import retrofit2.HttpException
import java.util.stream.Stream
import com.example.backend.entity.Customer as CustomerEntity

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class CustomerApiTests {

    @MockBean
    private lateinit var customerRepository: CustomerRepository

    private val factory = WebServiceFactory("http://localhost:8080/")
    private val customerService = factory.create<CustomerService>()

    @BeforeEach
    fun setUp() {
        factory.reset()
    }

    private suspend fun authenticate() {
        factory.authenticate {
            create<AuthenticationService>()
                .login(Credentials("user", "user_password"))
        }
    }

    private suspend fun authenticateAsAdmin() {
        factory.authenticate {
            create<AuthenticationService>()
                .login(Credentials("admin", "admin_password"))
        }
    }

    @Suppress("unused")
    companion object {

        // MethodSource

        @JvmStatic
        fun getAllCustomers(): Stream<Arguments> = Stream.of(
            listOf(),
            listOf(Customer(1, "F1", "L1"), Customer(2, "F2", "L2"))
        ).map { customers ->
            arguments(
                customers.map { CustomerEntity(it.id, it.firstName, it.lastName) },
                customers
            )
        }

        @JvmStatic
        fun getCustomer(): Stream<Arguments> = Stream.of(
            null,
            Customer(1, "F", "L")
        ).map { customer ->
            arguments(
                customer?.let { CustomerEntity(it.id, it.firstName, it.lastName) },
                customer
            )
        }

        @JvmStatic
        fun postCustomer(): Stream<Arguments> = Stream.of(
            Customer(null, "F", "L"),
            Customer(1, "F", "L"),
        ).map { customer ->
            arguments(customer)
        }
    }

    @ParameterizedTest
    @MethodSource
    fun getAllCustomers(entities: List<CustomerEntity>, expected: List<Customer>): Unit = runBlocking {
        authenticate()
        Mockito
            .`when`(customerRepository.findAll())
            .thenReturn(entities.asFlow())
        val customers = customerService.getAllCustomers()
        assertEquals(expected, customers)
    }

    @ParameterizedTest
    @MethodSource
    fun getCustomer(entity: CustomerEntity?, expected: Customer?): Unit = runBlocking {
        authenticate()
        Mockito
            .`when`(customerRepository.findById(1))
            .thenReturn(entity)
        val customer = customerService.getCustomer(1)
        assertEquals(expected, customer)
    }

    @ParameterizedTest
    @MethodSource("getAllCustomers")
    fun getCustomersByLastName(entities: List<CustomerEntity>, expected: List<Customer>): Unit = runBlocking {
        authenticate()
        Mockito
            .`when`(customerRepository.findByLastName("Last"))
            .thenReturn(entities.asFlow())
        val customers = customerService.getCustomersByLastName("Last")
        assertEquals(expected, customers)
    }

    @ParameterizedTest
    @MethodSource
    fun postCustomer(customer: Customer): Unit = runBlocking {
        authenticate()
        try {
            customerService.postCustomer(customer)
            fail()
        } catch (e: HttpException) {
            assertEquals(HttpStatus.FORBIDDEN.value(), e.code())
        }
    }

    @ParameterizedTest
    @MethodSource("postCustomer")
    fun postCustomerAsAdmin(customer: Customer): Unit = runBlocking {
        authenticateAsAdmin()
        Mockito
            .`when`(customerRepository.save(any(CustomerEntity::class.java)))
            .thenReturn(CustomerEntity(customer.id ?: 0, customer.firstName, customer.lastName))
        val savedCustomer = customerService.postCustomer(customer)
        assertEquals(customer.copy(id = customer.id ?: 0), savedCustomer)
    }
}