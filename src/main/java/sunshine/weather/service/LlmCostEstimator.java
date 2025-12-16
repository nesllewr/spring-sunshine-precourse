package sunshine.weather.service;

import org.springframework.stereotype.Component;
import sunshine.weather.config.LlmCostProperties;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class LlmCostEstimator {

    private final LlmCostProperties props;

    public LlmCostEstimator(LlmCostProperties props) {
        this.props = props;
    }

    public BigDecimal estimateUsd(long inputTokens, long outputTokens) {
        if (props.inputPer1k() == null || props.outputPer1k() == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal in = props.inputPer1k()
                .multiply(BigDecimal.valueOf(inputTokens))
                .divide(BigDecimal.valueOf(1000), 10, RoundingMode.HALF_UP);

        BigDecimal out = props.outputPer1k()
                .multiply(BigDecimal.valueOf(outputTokens))
                .divide(BigDecimal.valueOf(1000), 10, RoundingMode.HALF_UP);

        return in.add(out).setScale(6, RoundingMode.HALF_UP);
    }
}
