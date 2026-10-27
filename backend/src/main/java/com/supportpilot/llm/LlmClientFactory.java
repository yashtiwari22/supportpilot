package com.supportpilot.llm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Factory providing the configured LlmClient (Mock or Gemini).
 */
@Configuration
public class LlmClientFactory {

    @Bean
    @Primary
    public LlmClient primaryLlmClient(
            @Value("${supportpilot.llm.provider:mock}") String provider,
            ApplicationContext context) {
        if ("mock".equalsIgnoreCase(provider)) {
            return context.getBean("mockLlmClient", LlmClient.class);
        }
        // Fallback to mock if configured provider bean is missing
        return context.getBean("mockLlmClient", LlmClient.class);
    }
}
