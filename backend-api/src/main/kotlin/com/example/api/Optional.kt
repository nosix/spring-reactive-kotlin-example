package com.example.api

import kotlinx.serialization.Serializable

// FIXME https://github.com/square/retrofit/issues/3075

@Serializable
data class Optional<T>(val unboxed: T?)