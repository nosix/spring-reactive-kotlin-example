package com.example.backend.controller

import com.example.api.Optional
import com.example.api.Product
import com.example.api.ProductService
import com.example.backend.repository.ProductRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.springframework.web.bind.annotation.RestController
import com.example.backend.entity.Product as ProductEntity

@RestController
class ProductController(
    private val repository: ProductRepository
) : ProductService {

    private fun ProductEntity.asApi(): Product = Product(id, name, price)
    private suspend fun Flow<ProductEntity>.asApi(): List<Product> = map { it.asApi() }.toList()

    private fun Product.asEntity(): ProductEntity = ProductEntity(id, name, price)

    override suspend fun getProduct(id: Long): Optional<Product> {
        return Optional(repository.findById(id)?.asApi())
    }

    override suspend fun postProduct(product: Product): Product {
        return repository.save(product.asEntity()).asApi()
    }

    override suspend fun getProductByName(name: String?): List<Product> {
        return name?.let { repository.findByName(it).asApi() } ?: repository.findAll().asApi()
    }
}