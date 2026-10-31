package com.example.wallet.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Digital Wallet API",
                version = "1.0.0",
                description = "Wallet lifecycle, deposits, withdrawals, transfers and movement history. "
                        + "Financial POSTs require an Idempotency-Key header."
        )
)
public class OpenApiConfig {
}
