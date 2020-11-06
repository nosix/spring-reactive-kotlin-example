package com.example.backend.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.boot.autoconfigure.web.reactive.WebFluxRegistrations
import org.springframework.context.ApplicationContext
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.core.MethodParameter
import org.springframework.core.ReactiveAdapterRegistry
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.http.codec.HttpMessageReader
import org.springframework.util.StringValueResolver
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ValueConstants
import org.springframework.web.reactive.BindingContext
import org.springframework.web.reactive.result.method.RequestMappingInfo
import org.springframework.web.reactive.result.method.RequestMappingInfo.BuilderConfiguration
import org.springframework.web.reactive.result.method.annotation.ArgumentResolverConfigurer
import org.springframework.web.reactive.result.method.annotation.PathVariableMethodArgumentResolver
import org.springframework.web.reactive.result.method.annotation.RequestBodyMethodArgumentResolver
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerAdapter
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping
import org.springframework.web.reactive.result.method.annotation.RequestParamMethodArgumentResolver
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.reflect.KClass

@Configuration
class RetrofitAnnotationAdapter : WebFluxRegistrations {

    companion object {
        private val MAPPING_ANNOTATIONS: Set<KClass<out Annotation>> = setOf(
            GET::class,
            POST::class,
            PUT::class,
            DELETE::class
        )
    }

    override fun getRequestMappingHandlerMapping() = object : RequestMappingHandlerMapping() {

        private var embeddedValueResolver: StringValueResolver? = null
        private var config: BuilderConfiguration = BuilderConfiguration()

        override fun setEmbeddedValueResolver(resolver: StringValueResolver) {
            super.setEmbeddedValueResolver(resolver)
            this.embeddedValueResolver = resolver
        }

        override fun afterPropertiesSet() {
            config = BuilderConfiguration()
            config.setPatternParser(pathPatternParser)
            config.setContentTypeResolver(contentTypeResolver)
            super.afterPropertiesSet()
        }

        // createRequestMappingInfo が super class で private になっているので override する
        override fun getMappingForMethod(method: Method, handlerType: Class<*>): RequestMappingInfo? {
            val methodInfo = createRequestMappingInfo(method) ?: return null
            val typeInfo = createRequestMappingInfo(handlerType)
            val info = typeInfo?.combine(methodInfo) ?: methodInfo
            for (entry in pathPrefixes.entries) {
                if (entry.value.test(handlerType)) {
                    val prefix = embeddedValueResolver?.resolveStringValue(entry.key) ?: entry.key
                    return RequestMappingInfo.paths(prefix).options(config).build().combine(info)
                }
            }
            return info
        }

        // GetMapping など(RequestMapping)だけではなく、Retrofit の GET などのアノテーションに対応する
        private fun createRequestMappingInfo(element: AnnotatedElement): RequestMappingInfo? {
            val condition = when (element) {
                is Class<*> -> getCustomTypeCondition(element)
                is Method -> getCustomMethodCondition(element)
                else -> error("Invalid element type.")
            }
            val requestMapping = AnnotatedElementUtils.findMergedAnnotation(element, RequestMapping::class.java)
                ?: findInterfaceAnnotation(element)
                ?: return null
            return createRequestMappingInfo(requestMapping, condition)
        }

        private fun findInterfaceAnnotation(element: AnnotatedElement): RequestMapping? {
            element.forEachImplementingMethod {
                logger.info(toString())
                return when (val annotation = annotations.firstOrNull {
                    it.annotationClass in MAPPING_ANNOTATIONS
                }) {
                    is GET -> createRequestMapping(annotation.value, arrayOf(RequestMethod.GET))
                    is POST -> createRequestMapping(annotation.value, arrayOf(RequestMethod.POST))
                    is PUT -> createRequestMapping(annotation.value, arrayOf(RequestMethod.PUT))
                    is DELETE -> createRequestMapping(annotation.value, arrayOf(RequestMethod.DELETE))
                    else -> null
                }
            }

            return null
        }
    }

    override fun getRequestMappingHandlerAdapter() = object : RequestMappingHandlerAdapter() {

        private var applicationContext: ConfigurableApplicationContext? = null

        override fun setApplicationContext(applicationContext: ApplicationContext) {
            super.setApplicationContext(applicationContext)
            if (applicationContext is ConfigurableApplicationContext) {
                this.applicationContext = applicationContext
            }
        }

        override fun setArgumentResolverConfigurer(configurer: ArgumentResolverConfigurer?) {
            val beanFactory = applicationContext?.beanFactory
            reactiveAdapterRegistry?.let { registry ->
                configurer?.addCustomResolver(RequestParamMethodArgumentResolverWrapper(beanFactory, registry))
                configurer?.addCustomResolver(PathVariableMethodArgumentResolverWrapper(beanFactory, registry))
                configurer?.addCustomResolver(RequestBodyMethodArgumentResolverWrapper(messageReaders, registry))
            }
            super.setArgumentResolverConfigurer(configurer)
        }
    }

    private class RequestParamMethodArgumentResolverWrapper(
        beanFactory: ConfigurableBeanFactory?,
        registry: ReactiveAdapterRegistry
    ) : RequestParamMethodArgumentResolver(beanFactory, registry, false) {

        override fun supportsParameter(parameter: MethodParameter): Boolean {
            return super.supportsParameter(HandlerMethodParameterWrapper(parameter))
        }

        override fun resolveArgument(
            parameter: MethodParameter,
            bindingContext: BindingContext,
            exchange: ServerWebExchange
        ): Mono<Any> {
            return super.resolveArgument(HandlerMethodParameterWrapper(parameter), bindingContext, exchange)
        }
    }

    private class PathVariableMethodArgumentResolverWrapper(
        beanFactory: ConfigurableBeanFactory?,
        registry: ReactiveAdapterRegistry
    ) : PathVariableMethodArgumentResolver(beanFactory, registry) {

        override fun supportsParameter(parameter: MethodParameter): Boolean {
            return super.supportsParameter(HandlerMethodParameterWrapper(parameter))
        }

        override fun resolveArgument(
            parameter: MethodParameter,
            bindingContext: BindingContext,
            exchange: ServerWebExchange
        ): Mono<Any> {
            return super.resolveArgument(HandlerMethodParameterWrapper(parameter), bindingContext, exchange)
        }
    }

    private class RequestBodyMethodArgumentResolverWrapper(
        readers: List<HttpMessageReader<*>>,
        registry: ReactiveAdapterRegistry
    ) : RequestBodyMethodArgumentResolver(readers, registry) {

        override fun supportsParameter(parameter: MethodParameter): Boolean {
            return super.supportsParameter(HandlerMethodParameterWrapper(parameter))
        }

        override fun resolveArgument(
            parameter: MethodParameter,
            bindingContext: BindingContext,
            exchange: ServerWebExchange
        ): Mono<Any> {
            return super.resolveArgument(HandlerMethodParameterWrapper(parameter), bindingContext, exchange)
        }
    }

    private class HandlerMethodParameterWrapper(parameter: MethodParameter) : MethodParameter(parameter) {

        // Query -> RequestParam
        // Path -> PathVariable
        // Body -> RequestBody
        override fun getParameterAnnotations(): Array<Annotation> {
            val annotations = super.getParameterAnnotations().toMutableList()
            var hasRequestParam = false
            var hasPathVariable = false
            var hasRequestBody = false
            for (annotation in annotations) {
                when (annotation) {
                    is RequestParam -> hasRequestParam = true
                    is PathVariable -> hasPathVariable = true
                    is RequestBody -> hasRequestBody = true
                }
            }
            if (!hasPathVariable) {
                method?.forEachImplementingMethod {
                    parameters[parameterIndex].getAnnotation(Path::class.java)?.let {
                        annotations.add(createPathVariableAnnotation(it.value))
                    }
                }
            }
            if (!hasRequestParam) {
                method?.forEachImplementingMethod {
                    parameters[parameterIndex].getAnnotation(Query::class.java)?.let {
                        annotations.add(createRequestParamAnnotation(it.value))
                    }
                }
            }
            if (!hasRequestBody) {
                method?.forEachImplementingMethod {
                    parameters[parameterIndex].getAnnotation(Body::class.java)?.let {
                        annotations.add(createRequestBodyAnnotation())
                    }
                }
            }
            return annotations.toTypedArray()
        }
    }
}

private inline fun AnnotatedElement.forEachImplementingMethod(action: Method.() -> Unit) {
    if (this !is Method) return
    declaringClass.interfaces.forEach { spec ->
        try {
            spec.getMethod(name, *parameterTypes).action()
        } catch (e: NoSuchMethodException) {
        }
    }
}

private inline fun <reified T : Annotation> create(
    crossinline methodImpl: Method.(args: Array<Any>?) -> Any?
): T {
    return Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
        method?.methodImpl(args)
    } as T
}

private fun createRequestMapping(path: String, method: Array<RequestMethod>): RequestMapping {
    return create {
        when (this.name) {
            "value" -> arrayOf(path)
            "path" -> arrayOf(path)
            "name" -> ""
            "method" -> method
            "params" -> emptyArray<String>()
            "headers" -> emptyArray<String>()
            "consumes" -> emptyArray<String>()
            "produces" -> emptyArray<String>()
            "toString" -> RequestMapping::class.qualifiedName
            else -> error("'${this.name}' is not supported.")
        }
    }
}

private fun createRequestParamAnnotation(name: String): RequestParam {
    return create { args ->
        val properties = mapOf(
            "value" to name,
            "name" to name,
            "required" to true, // TODO support optional
            "defaultValue" to ValueConstants.DEFAULT_NONE
        )
        when (this.name) {
            "annotationType" -> RequestParam::class.java
            "toString" -> RequestParam::class.qualifiedName
            "hashCode" -> properties.hashCode()
            "equals" -> args != null &&
                    (args[0] as? RequestParam)?.let { other ->
                        other.name == name
                    } ?: false
            in properties -> properties[this.name]
            else -> error("'${this.name}' is not supported.")
        }
    }
}

private fun createPathVariableAnnotation(name: String): PathVariable {
    return create { args ->
        val properties = mapOf(
            "value" to name,
            "name" to name,
            "required" to true // TODO support optional
        )
        when (this.name) {
            "annotationType" -> PathVariable::class.java
            "toString" -> PathVariable::class.qualifiedName
            "hashCode" -> properties.hashCode()
            "equals" -> args != null &&
                    (args[0] as? PathVariable)?.let { other ->
                        other.name == name
                    } ?: false
            in properties -> properties[this.name]
            else -> error("'${this.name}' is not supported.")
        }
    }
}

private fun createRequestBodyAnnotation(): RequestBody {
    return create { args ->
        val properties = mapOf(
            "required" to true // TODO support optional
        )
        when (this.name) {
            "annotationType" -> RequestBody::class.java
            "toString" -> RequestBody::class.qualifiedName
            "hashCode" -> properties.hashCode()
            "equals" -> args != null &&
                    (args[0] as? RequestBody)?.let {
                        true
                    } ?: false
            in properties -> properties[this.name]
            else -> error("'${this.name}' is not supported.")
        }
    }
}