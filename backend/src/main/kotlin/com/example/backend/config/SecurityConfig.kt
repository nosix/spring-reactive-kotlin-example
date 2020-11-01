package com.example.backend.config

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.example.api.Credentials
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.codec.HttpMessageReader
import org.springframework.http.codec.ServerCodecConfigurer
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.security.authentication.ReactiveAuthenticationManager
import org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.authorization.AuthorizationDecision
import org.springframework.security.authorization.ReactiveAuthorizationManager
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.SecurityWebFiltersOrder
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.Authentication
import org.springframework.security.core.userdetails.ReactiveUserDetailsService
import org.springframework.security.core.userdetails.User
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.ServerAuthenticationEntryPoint
import org.springframework.security.web.server.authentication.AuthenticationWebFilter
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler
import org.springframework.security.web.server.authorization.AuthorizationContext
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers
import org.springframework.web.reactive.function.BodyExtractor
import org.springframework.web.reactive.function.BodyExtractors
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import reactor.core.publisher.Mono
import java.util.*
import kotlin.random.Random
import kotlin.time.ExperimentalTime
import kotlin.time.minutes

@EnableWebFluxSecurity
@Configuration
class SecurityConfig {

    private companion object {
        private const val AUTHORIZATION_TYPE = "Bearer"
    }

    private val loginPath = "/login"
    private val adminPath = "/admin"
    private val secret = Base64.getUrlEncoder().encodeToString(Random.nextBytes(128 / 8))
    private val algorithm = Algorithm.HMAC512(secret)

    @Bean
    fun passwordEncoder(): PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()

    @Bean
    fun authenticationManager(
        userDetailsService: ReactiveUserDetailsService,
        passwordEncoder: PasswordEncoder
    ): ReactiveAuthenticationManager {
        return UserDetailsRepositoryReactiveAuthenticationManager(userDetailsService).apply {
            setPasswordEncoder(passwordEncoder) // デフォルトは DelegatingPasswordEncoder
        }
    }

    @Bean
    fun springSecurityFilterChain(
        http: ServerHttpSecurity,
        authenticationManager: ReactiveAuthenticationManager,
        serverCodecConfigurer: ServerCodecConfigurer
    ): SecurityWebFilterChain {

        http.csrf().disable()
        http.httpBasic().disable()
        http.formLogin().disable()
        http.logout().disable()

        // 認証(authentication)の設定
        val authenticationFilter = createAuthenticationWebFilter(
            authenticationManager,
            serverCodecConfigurer,
            ServerWebExchangeMatchers.pathMatchers(HttpMethod.POST, loginPath)
        )
        http.addFilterAt(authenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)

        // 認可(authorization)の設定
        http.authorizeExchange()
            .pathMatchers(loginPath).permitAll()
            .pathMatchers(adminPath).hasRole("ADMIN")
            .anyExchange().access(authorizationManager)

        // 認可で失敗した場合の応答
        http.exceptionHandling()
            .authenticationEntryPoint(authenticationEntryPoint)
            .accessDeniedHandler(accessDeniedHandler)

        return http.build()
    }

    // @Bean にしない (WebFilter を重複登録させないため)
    fun createAuthenticationWebFilter(
        authenticationManager: ReactiveAuthenticationManager,
        serverCodecConfigurer: ServerCodecConfigurer,
        loginPath: ServerWebExchangeMatcher
    ): WebFilter {
        return AuthenticationWebFilter(authenticationManager).apply {
            // 認証処理を行うリクエスト
            setRequiresAuthenticationMatcher(loginPath)
            // 認証処理における認証情報を抽出方法
            setServerAuthenticationConverter(JsonBodyAuthenticationConverter(serverCodecConfigurer.readers))
            // 認証(成功/失敗)時の処理
            setAuthenticationSuccessHandler(authenticationSuccessHandler)
            setAuthenticationFailureHandler(authenticationFailureHandler)
            // セキュリティコンテキストの保存方法 (保存しない)
            setSecurityContextRepository(NoOpServerSecurityContextRepository.getInstance())
        }
    }

    class JsonBodyAuthenticationConverter(
        val messageReaders: List<HttpMessageReader<*>>
    ) : ServerAuthenticationConverter {
        override fun convert(exchange: ServerWebExchange): Mono<Authentication> {
            return BodyExtractors
                .toMono(String::class.java)
                .extract(exchange.request, object : BodyExtractor.Context {
                    override fun messageReaders(): List<HttpMessageReader<*>> = messageReaders
                    override fun serverResponse(): Optional<ServerHttpResponse> = Optional.of(exchange.response)
                    override fun hints(): Map<String, Any> = mapOf()
                })
                .map {
                    val auth = Json.decodeFromString<Credentials>(it)
                    UsernamePasswordAuthenticationToken(auth.mailAddress, auth.password)
                }
        }
    }

    // 未認証時の応答
    private val authenticationEntryPoint =
        ServerAuthenticationEntryPoint { exchange, _ ->
            Mono.fromRunnable {
                exchange.response.statusCode = HttpStatus.UNAUTHORIZED
            }
        }

    // 認証成功時の応答
    private val authenticationSuccessHandler =
        ServerAuthenticationSuccessHandler { webFilterExchange, authentication ->
            Mono.fromRunnable {
                val token = authentication.toTokenBy(algorithm)
                with(webFilterExchange.exchange.response) {
                    statusCode = HttpStatus.OK
                    headers[HttpHeaders.AUTHORIZATION] = "$AUTHORIZATION_TYPE $token"
                }
            }
        }

    // 認証失敗時の応答
    private val authenticationFailureHandler =
        ServerAuthenticationFailureHandler { webFilterExchange, _ ->
            Mono.fromRunnable {
                webFilterExchange.exchange.response.statusCode = HttpStatus.FORBIDDEN
            }
        }

    // 認可拒否時の応答
    private val accessDeniedHandler =
        ServerAccessDeniedHandler { exchange, _ ->
            Mono.fromRunnable {
                exchange.response.statusCode = HttpStatus.FORBIDDEN
            }
        }

    @OptIn(ExperimentalTime::class)
    private val expirationTime: Long = 10.minutes.toLongMilliseconds()

    private fun Authentication.toTokenBy(algorithm: Algorithm): String {
        val user = principal as? User ?: throw AssertionError("The principal must be User. [$principal]")
        val issuedAt = Date()
        val notBefore = Date(issuedAt.time)
        val expiresAt = Date(issuedAt.time + expirationTime)
        return JWT.create()
            .withSubject(user.username)
            .withIssuedAt(issuedAt)
            .withNotBefore(notBefore)
            .withExpiresAt(expiresAt)
            .sign(algorithm)
    }

    private val authorizationManager =
        ReactiveAuthorizationManager<AuthorizationContext> { _, context ->
            Mono.just(AuthorizationDecision(true))
                .filter { context.exchange.request.token?.let(::verify) ?: false }
        }

    private val ServerHttpRequest.token: String?
        get() {
            val authHeader: String = headers[HttpHeaders.AUTHORIZATION]?.firstOrNull() ?: return null
            if (!authHeader.startsWith("$AUTHORIZATION_TYPE ")) return null
            return authHeader.substring(AUTHORIZATION_TYPE.length + 1)
        }

    private fun verify(token: String): Boolean {
        val verifier = JWT.require(algorithm).build()
        val jwt = verifier.verify(token)
        // TODO check some check
        return true
    }
}