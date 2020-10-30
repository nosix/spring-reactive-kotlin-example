package com.example.backend.service

import org.springframework.security.core.userdetails.MapReactiveUserDetailsService
import org.springframework.security.core.userdetails.User
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class UserDetailsService(
    passwordEncoder: PasswordEncoder
) : MapReactiveUserDetailsService(
    User.withUsername("user")
        .passwordEncoder(passwordEncoder::encode)
        .password("user_password")
        .roles("USER")
        .build(),
    User.withUsername("admin")
        .passwordEncoder(passwordEncoder::encode)
        .password("admin_password")
        .roles("USER", "ADMIN")
        .build()
)