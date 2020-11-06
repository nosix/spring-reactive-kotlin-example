package com.example.backend

import com.example.WebServiceFactory
import com.example.api.AuthenticationService
import com.example.api.Credentials
import com.example.api.Product
import com.example.api.ProductService
import com.example.backend.repository.ProductRepository
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import com.example.backend.entity.Product as ProductEntity

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class ProductApiTests {

    @MockBean
    private lateinit var productRepository: ProductRepository

    private val factory = WebServiceFactory("http://localhost:8080/")
    private val productService = factory.create<ProductService>()

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

    @Test
    fun getProduct(): Unit = runBlocking {
        val expected = Product(1, "N1", 0)
        val entity = expected.run { ProductEntity(id, name, price) }
        authenticate()
        Mockito
            .`when`(productRepository.findById(1))
            .thenReturn(entity)
        val product = productService.getProduct(1).unboxed
        assertEquals(expected, product)
    }

    @Test
    fun getProductsByName(): Unit = runBlocking {
        val expected = listOf(Product(1, "N1", 0), Product(2, "N2", 1))
        val entities = expected.map { ProductEntity(it.id, it.name, it.price) }
        authenticate()
        Mockito
            .`when`(productRepository.findByName("N"))
            .thenReturn(entities.asFlow())
        val products = productService.getProductByName("N")
        assertEquals(expected, products)
    }

    @Test
    fun getAllProducts(): Unit = runBlocking {
        val expected = listOf(Product(1, "N1", 0), Product(2, "N2", 1))
        val entities = expected.map { ProductEntity(it.id, it.name, it.price) }
        authenticate()
        Mockito
            .`when`(productRepository.findAll())
            .thenReturn(entities.asFlow())
        val products = productService.getProductByName()
        assertEquals(expected, products)
    }

    @Test
    fun postProduct(): Unit = runBlocking {
        val product = Product(1, "N1", 0)
        authenticate()
        Mockito
            .`when`(productRepository.save(Mockito.any(ProductEntity::class.java)))
            .thenReturn(ProductEntity(product.id ?: 0, product.name, product.price))
        val savedProduct = productService.postProduct(product)
        assertEquals(product.copy(id = product.id ?: 0), savedProduct)
    }
}