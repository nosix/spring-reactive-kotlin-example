package com.example.backend.config.retrofit

import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ValueConstants
import retrofit2.http.Body
import retrofit2.http.Path
import retrofit2.http.Query
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

internal inline fun AnnotatedElement.forEachImplementingMethod(action: Method.() -> Unit) {
    if (this !is Method) return
    declaringClass.interfaces.forEach { spec ->
        try {
            spec.getMethod(name, *parameterTypes).action()
        } catch (e: NoSuchMethodException) {
        }
    }
}

private inline fun <reified R> newInstance(handler: InvocationHandler): R {
    return Proxy.newProxyInstance(R::class.java.classLoader, arrayOf(R::class.java), handler) as R
}

private inline fun <reified T : Annotation, reified R : Annotation> T.invocationHandler(
    properties: Map<String, Any?>,
    crossinline equalsImpl: T.(R) -> Boolean
) = InvocationHandler { _, method, args ->
    when (method.name) {
        "annotationType" -> R::class.java
        "toString" -> R::class.qualifiedName
        "hashCode" -> properties.hashCode()
        "equals" -> args != null && (args[0] as? R)?.let { equalsImpl(it) } ?: false
        in properties -> properties[method.name]
        else -> error("'${method.name}' is not supported.")
    }
}

class RetrofitRequestMapping(
    private val path: String,
    private val method: RequestMethod
) : Annotation {

    fun toRequestMapping(): RequestMapping {
        val pathValue = arrayOf(path)
        val methodValue = arrayOf(method)
        return newInstance(invocationHandler<RetrofitRequestMapping, RequestMapping>(
            properties = mapOf(
                "value" to pathValue,
                "path" to pathValue,
                "name" to "",
                "method" to methodValue,
                "params" to emptyArray<String>(),
                "headers" to emptyArray<String>(),
                "consumes" to emptyArray<String>(),
                "produces" to emptyArray<String>()
            ),
            equalsImpl = {
                (it.path.size == 1 && it.path.first() == path) &&
                        (it.method.size == 1 && it.method.first() == method)
            }
        ))
    }
}

fun Query.toRequestParam(): RequestParam {
    return newInstance(invocationHandler<Query, RequestParam>(
        properties = mapOf(
            "value" to value,
            "name" to value,
            "required" to true,
            "defaultValue" to ValueConstants.DEFAULT_NONE
        ),
        equalsImpl = {
            it.name == value && it.required && it.defaultValue == ValueConstants.DEFAULT_NONE
        }
    ))
}

fun Path.toPathVariable(): PathVariable {
    return newInstance(invocationHandler<Path, PathVariable>(
        properties = mapOf(
            "value" to value,
            "name" to value,
            "required" to true
        ),
        equalsImpl = {
            it.name == value && it.required
        }
    ))
}

fun Body.toRequestBody(): RequestBody {
    return newInstance(invocationHandler<Body, RequestBody>(
        properties = mapOf(
            "required" to true
        ),
        equalsImpl = {
            it.required
        }
    ))
}