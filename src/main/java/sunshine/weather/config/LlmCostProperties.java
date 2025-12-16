package sunshine.weather.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "app.llm-cost")
public record LlmCostProperties(
        BigDecimal inputPer1k,
        BigDecimal outputPer1k
) {}
