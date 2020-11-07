package com.example.backend.config.retrofit

import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.ApplicationContext
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.MethodParameter
import org.springframework.core.ReactiveAdapterRegistry
import org.springframework.http.codec.HttpMessageReader
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.reactive.BindingContext
import org.springframework.web.reactive.result.method.annotation.ArgumentResolverConfigurer
import org.springframework.web.reactive.result.method.annotation.PathVariableMethodArgumentResolver
import org.springframework.web.reactive.result.method.annotation.RequestBodyMethodArgumentResolver
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerAdapter
import org.springframework.web.reactive.result.method.annotation.RequestParamMethodArgumentResolver
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import retrofit2.http.Body
import retrofit2.http.Path
import retrofit2.http.Query

class RetrofitRequestMappingHandlerAdapter : RequestMappingHandlerAdapter() {

    private var applicationContext: ConfigurableApplicationContext? = null

    override fun setApplicationContext(applicationContext: ApplicationContext) {
        super.setApplicationContext(applicationContext)
        if (applicationContext is ConfigurableApplicationContext) {
            this.applicationContext = applicationContext
        }
    }

    override fun setArgumentResolverConfigurer(configurer: ArgumentResolverConfigurer?) {
        if (configurer != null) {
            val beanFactory = applicationContext?.beanFactory
            reactiveAdapterRegistry?.let { registry ->
                configurer.addCustomResolver(RequestParamMethodArgumentResolverWrapper(beanFactory, registry))
                configurer.addCustomResolver(PathVariableMethodArgumentResolverWrapper(beanFactory, registry))
                configurer.addCustomResolver(RequestBodyMethodArgumentResolverWrapper(messageReaders, registry))
            }
        }
        super.setArgumentResolverConfigurer(configurer)
    }

    private class RequestParamMethodArgumentResolverWrapper(
        beanFactory: ConfigurableBeanFactory?,
        registry: ReactiveAdapterRegistry
    ) : RequestParamMethodArgumentResolver(beanFactory, registry, false) {

        override fun supportsParameter(parameter: MethodParameter): Boolean {
            return super.supportsParameter(MethodParameterWrapper(parameter))
        }

        override fun resolveArgument(
            parameter: MethodParameter,
            bindingContext: BindingContext,
            exchange: ServerWebExchange
        ): Mono<Any> {
            return super.resolveArgument(MethodParameterWrapper(parameter), bindingContext, exchange)
        }
    }

    private class PathVariableMethodArgumentResolverWrapper(
        beanFactory: ConfigurableBeanFactory?,
        registry: ReactiveAdapterRegistry
    ) : PathVariableMethodArgumentResolver(beanFactory, registry) {

        override fun supportsParameter(parameter: MethodParameter): Boolean {
            return super.supportsParameter(MethodParameterWrapper(parameter))
        }

        override fun resolveArgument(
            parameter: MethodParameter,
            bindingContext: BindingContext,
            exchange: ServerWebExchange
        ): Mono<Any> {
            return super.resolveArgument(MethodParameterWrapper(parameter), bindingContext, exchange)
        }
    }

    private class RequestBodyMethodArgumentResolverWrapper(
        readers: List<HttpMessageReader<*>>,
        registry: ReactiveAdapterRegistry
    ) : RequestBodyMethodArgumentResolver(readers, registry) {

        override fun supportsParameter(parameter: MethodParameter): Boolean {
            return super.supportsParameter(MethodParameterWrapper(parameter))
        }

        override fun resolveArgument(
            parameter: MethodParameter,
            bindingContext: BindingContext,
            exchange: ServerWebExchange
        ): Mono<Any> {
            return super.resolveArgument(MethodParameterWrapper(parameter), bindingContext, exchange)
        }
    }

    private class MethodParameterWrapper(parameter: MethodParameter) : MethodParameter(parameter) {

        private var parameterAnnotations: Array<Annotation>? = null

        // Query -> RequestParam
        // Path -> PathVariable
        // Body -> RequestBody
        override fun getParameterAnnotations(): Array<Annotation> {
            return parameterAnnotations ?: run {
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
                method?.forEachImplementingMethod {
                    parameters[parameterIndex].run {
                        if (!hasRequestParam) {
                            getAnnotation(Query::class.java)?.let {
                                annotations.add(it.toRequestParam())
                                hasRequestParam = true
                            }
                        }
                        if (!hasPathVariable) {
                            getAnnotation(Path::class.java)?.let {
                                annotations.add(it.toPathVariable())
                                hasPathVariable = true
                            }
                        }
                        if (!hasRequestBody) {
                            getAnnotation(Body::class.java)?.let {
                                annotations.add(it.toRequestBody())
                                hasRequestBody = true
                            }
                        }
                    }
                }
                annotations.toTypedArray().also {
                    parameterAnnotations = it
                }
            }
        }
    }
}