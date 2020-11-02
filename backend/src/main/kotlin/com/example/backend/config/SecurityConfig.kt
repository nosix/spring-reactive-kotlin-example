package com.example.backend.config

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.example.api.Credentials
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
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
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.SecurityWebFiltersOrder
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextImpl
import org.springframework.security.core.userdetails.ReactiveUserDetailsService
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.ServerAuthenticationEntryPoint
import org.springframework.security.web.server.authentication.AuthenticationWebFilter
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler
import org.springframework.security.web.server.context.ServerSecurityContextRepository
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers
import org.springframework.web.reactive.function.BodyExtractor
import org.springframework.web.reactive.function.BodyExtractors
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import reactor.core.publisher.Mono
import java.util.*
import kotlin.random.Random
import kotlin.streams.toList
import kotlin.time.ExperimentalTime
import kotlin.time.minutes

@EnableWebFluxSecurity
@Configuration
class SecurityConfig {

    private companion object {
        private const val AUTHORIZATION_TYPE = "Bearer"
    }

    private val loginPath = "/login"
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

        val securityContextRepository = JwtTokenSecurityContextRepository(algorithm)
        http.securityContextRepository(securityContextRepository)

        // 認証(authentication)の設定
        val authenticationFilter = createAuthenticationWebFilter(
            authenticationManager,
            serverCodecConfigurer,
            securityContextRepository,
            ServerWebExchangeMatchers.pathMatchers(HttpMethod.POST, loginPath)
        )
        http.addFilterAt(authenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)

        // 認可(authorization)の設定
        http.authorizeExchange()
            .pathMatchers(loginPath).permitAll()
            .pathMatchers(HttpMethod.POST, "/customers").hasRole("ADMIN")
            .anyExchange().authenticated()

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
        securityContextRepository: ServerSecurityContextRepository,
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
            // セキュリティコンテキストの保存方法
            setSecurityContextRepository(securityContextRepository)
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
        ServerAuthenticationSuccessHandler { webFilterExchange, _ ->
            Mono.fromRunnable {
                webFilterExchange.exchange.response.statusCode = HttpStatus.OK
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

    private class JwtTokenSecurityContextRepository(
        private val algorithm: Algorithm
    ) : ServerSecurityContextRepository {

        private val logger = LoggerFactory.getLogger(this::class.java)

        @OptIn(ExperimentalTime::class)
        private val expirationTime: Long = 10.minutes.toLongMilliseconds()

        override fun save(exchange: ServerWebExchange, context: SecurityContext): Mono<Void> {
            return Mono.fromRunnable {
                val token = context.authentication.toTokenBy(algorithm)
                with(exchange.response) {
                    headers[HttpHeaders.AUTHORIZATION] = "$AUTHORIZATION_TYPE $token"
                }
            }
        }

        private fun Authentication.toTokenBy(algorithm: Algorithm): String {
            val user = principal as? User ?: throw AssertionError("The principal must be User. [$principal]")
            val issuedAt = Date()
            val notBefore = Date(issuedAt.time)
            val expiresAt = Date(issuedAt.time + expirationTime)
            return JWT.create()
                .withSubject(Json.encodeToString(UserDetailsSerializer, user))
                .withIssuedAt(issuedAt)
                .withNotBefore(notBefore)
                .withExpiresAt(expiresAt)
                .sign(algorithm)
        }

        override fun load(exchange: ServerWebExchange): Mono<SecurityContext> {
            return Mono.fromCallable {
                exchange.request.token?.let(::verify)?.let { authentication ->
                    SecurityContextImpl(authentication)
                }
            }
        }

        private val ServerHttpRequest.token: String?
            get() {
                val authHeader: String = headers[HttpHeaders.AUTHORIZATION]?.firstOrNull() ?: return null
                if (!authHeader.startsWith("$AUTHORIZATION_TYPE ")) return null
                return authHeader.substring(AUTHORIZATION_TYPE.length + 1)
            }

        private fun verify(token: String): Authentication? {
            val verifier = JWT.require(algorithm).build()
            return try {
                val jwt = verifier.verify(token)
                val user = Json.decodeFromString(UserDetailsSerializer, jwt.subject)
                logger.debug("verified: $user")
                UsernamePasswordAuthenticationToken(user, null, user.authorities)
            } catch (e: Exception) {
                logger.debug("failed verifying: ${e.message}")
                null
            }
        }
    }

    private object UserDetailsSerializer : KSerializer<UserDetails> {

        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("UserDetails") {
            element<String>("u", emptyList(), false)
            element<String>("p", emptyList(), false)
            element<Boolean>("e", emptyList(), false)
            element<Boolean>("ane", emptyList(), false)
            element<Boolean>("cne", emptyList(), false)
            element<Boolean>("anl", emptyList(), false)
            element<List<String>>("a", emptyList(), false)
        }

        override fun serialize(encoder: Encoder, value: UserDetails) {
            encoder.encodeStructure(descriptor) {
                encodeStringElement(descriptor, 0, value.username)
                encodeStringElement(descriptor, 1, value.password)
                encodeBooleanElement(descriptor, 2, value.isEnabled)
                encodeBooleanElement(descriptor, 3, value.isAccountNonExpired)
                encodeBooleanElement(descriptor, 4, value.isCredentialsNonExpired)
                encodeBooleanElement(descriptor, 5, value.isAccountNonLocked)
                encodeSerializableElement(
                    descriptor, 6,
                    ListSerializer(String.serializer()),
                    value.authorities.stream().map { it.authority }.toList()
                )
            }
        }

        override fun deserialize(decoder: Decoder): UserDetails {
            val builder = User.builder()
            decoder.decodeStructure(descriptor) {
                while (true) {
                    when (val index = decodeElementIndex(descriptor)) {
                        0 -> builder.username(decodeStringElement(descriptor, 0))
                        1 -> builder.password(decodeStringElement(descriptor, 1))
                        2 -> builder.disabled(!decodeBooleanElement(descriptor, 2))
                        3 -> builder.accountExpired(!decodeBooleanElement(descriptor, 3))
                        4 -> builder.credentialsExpired(!decodeBooleanElement(descriptor, 4))
                        5 -> builder.accountLocked(!decodeBooleanElement(descriptor, 5))
                        6 -> builder.authorities(
                            decodeSerializableElement(descriptor, 6, ListSerializer(String.serializer()))
                                .map { SimpleGrantedAuthority(it) }
                        )
                        CompositeDecoder.DECODE_DONE -> break
                        else -> error("Unexpected index: $index")
                    }
                }
            }
            return builder.build()
        }
    }
}