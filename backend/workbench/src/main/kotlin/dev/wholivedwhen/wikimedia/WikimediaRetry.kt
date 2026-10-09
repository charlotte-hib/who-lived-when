package dev.wholivedwhen.wikimedia

import io.github.resilience4j.common.retry.configuration.RetryConfigCustomizer
import io.github.resilience4j.core.IntervalBiFunction
import io.github.resilience4j.retry.RetryConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** The wait between two attempts of a call to Wikimedia: what its `Retry-After` asked for, else the default. */
@Configuration(proxyBeanMethods = false)
class WikimediaRetry {

    @Bean
    fun wikimediaRetryAfter(properties: WikimediaProperties): RetryConfigCustomizer =
        RetryConfigCustomizer.of(WikimediaClient.PACE) { builder ->
            @Suppress("UNCHECKED_CAST")
            (builder as RetryConfig.Builder<Any?>).intervalBiFunction(IntervalBiFunction { _, outcome ->
                val asked = outcome.fold({ (it as? WikimediaBusyException)?.retryAfter }, { null })
                (asked ?: properties.defaultRetryWait).toMillis()
            })
        }
}
