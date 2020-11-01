package com.example.backend.config

import org.springframework.boot.autoconfigure.codec.CodecProperties
import org.springframework.boot.context.properties.PropertyMapper
import org.springframework.boot.web.codec.CodecCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.codec.CodecConfigurer
import org.springframework.http.codec.DecoderHttpMessageReader
import org.springframework.http.codec.EncoderHttpMessageWriter
import org.springframework.http.codec.json.Jackson2JsonDecoder
import org.springframework.http.codec.json.Jackson2JsonEncoder
import org.springframework.http.codec.json.KotlinSerializationJsonDecoder
import org.springframework.http.codec.json.KotlinSerializationJsonEncoder
import org.springframework.util.unit.DataSize

@Configuration
class CodecConfig {

    @Bean
    @Order(0)
    fun codecCustomizer(codecProperties: CodecProperties) =
        CodecCustomizer { configurer: CodecConfigurer ->
            // DefaultCodecs を設定する
            val defaultCodecs = configurer.defaultCodecs()
            defaultCodecs.enableLoggingRequestDetails(codecProperties.isLogRequestDetails)
            PropertyMapper.get()
                .from(codecProperties.maxInMemorySize)
                .whenNonNull()
                .asInt(DataSize::toBytes)
                .to(defaultCodecs::maxInMemorySize)

            // DefaultCodecs の Reader に Jackson が含まれる場合、Kotlin Serialization に置換する
            val readers = configurer.readers.apply {
                val indexOfJackson = indexOfFirst {
                    it is DecoderHttpMessageReader && it.decoder is Jackson2JsonDecoder
                }
                if (indexOfJackson > 0) {
                    set(indexOfJackson, DecoderHttpMessageReader(KotlinSerializationJsonDecoder()))
                }
            }

            // DefaultCodecs の Writer に Jackson が含まれる場合、Kotlin Serialization に置換する
            val writers = configurer.writers.apply {
                val indexOfJackson = indexOfFirst {
                    it is EncoderHttpMessageWriter && it.encoder is Jackson2JsonEncoder
                }
                if (indexOfJackson > 0) {
                    set(indexOfJackson, EncoderHttpMessageWriter(KotlinSerializationJsonEncoder()))
                }
            }

            // DefaultCodecs を無効化し、編集した DefaultCodecs を CustomCodecs として登録する
            configurer.registerDefaults(false)
            configurer.customCodecs().run {
                readers.forEach(::register)
                writers.forEach(::register)
            }
        }
}