package com.example.backend.config

import com.example.backend.config.retrofit.RetrofitRequestMappingHandlerAdapter
import com.example.backend.config.retrofit.RetrofitRequestMappingHandlerMapping
import org.springframework.boot.autoconfigure.web.reactive.WebFluxRegistrations
import org.springframework.context.annotation.Configuration

/**
 * interface に付与された Retrofit annotation を Spring annotation に変換する
 *
 * ## 変換規則 (Retrofit -> Spring)
 * - GET,POST,PUT,DELETE -> RequestMapping
 * - Query -> RequestParam
 * - Path -> PathVariable
 * - Body -> RequestBody
 *
 * ## 使用方法
 * Retrofit annotation が付与された interface を controller class で実装する。
 * override した method の annotation が controller class の method に付与される。
 * ```kotlin
 * @RestController
 * class XxxController : XxxRetrofitInterface {
 *   override doAnything(id: Long): Xxx
 * }
 * ```
 */
@Configuration
class RetrofitConfig : WebFluxRegistrations {
    override fun getRequestMappingHandlerMapping() = RetrofitRequestMappingHandlerMapping()
    override fun getRequestMappingHandlerAdapter() = RetrofitRequestMappingHandlerAdapter()
}