package sunshine.weather.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;
import sunshine.weather.dto.ForecastResponse;
import sunshine.weather.model.City;
import sunshine.weather.model.WeatherCode;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

@Component
public class LlmWeatherAdvisor {

    private static final Logger log = LoggerFactory.getLogger(LlmWeatherAdvisor.class);

    private final ChatClient chatClient;
    private final LlmCostEstimator llmCostEstimator;

    public LlmWeatherAdvisor(ChatClient.Builder chatClientBuilder, LlmCostEstimator llmCostEstimator) {
        this.chatClient = chatClientBuilder.build();
        this.llmCostEstimator = llmCostEstimator;
    }

    public Advice advise(City city, ForecastResponse.Current w) {
        var converter = new BeanOutputConverter<>(Advice.class);
        var format = converter.getFormat();

        String weatherDesc = WeatherCode.getDescription(w.weather_code());

        var userMessage = """
                너는 한국어로 답하는 날씨 리포터이자 스타일리스트야.
                입력된 "현재 날씨 수치"를 바탕으로
                1) 날씨 요약(weatherSummary)
                2) 옷차림 추천(outfitSummary)
                을 생성해.

                제약:
                - 반드시 {format} 형식만 출력 (다른 텍스트 금지)
                - weatherSummary는 2~3문장, 수치(기온/체감/습도/풍속)와 상태를 자연스럽게 포함
                - outfitSummary는 2~4문장, 기온/체감/바람/강수 가능성을 근거로 추천
                - 과장, 단정적 예보 금지(“가능성”, “권장” 등 안전한 표현)
                - 브랜드 언급 금지

                [도시]
                - 이름: {cityName}

                [현재 날씨]
                - 기온: {t}°C
                - 체감: {a}°C
                - 습도: {h}%
                - 풍속: {ws}m/s
                - 상태: {desc} (code={code})

                {format}
                """;

        var promptTemplate = new PromptTemplate(userMessage);
        var prompt = promptTemplate.create(Map.of(
                "format", format,
                "cityName", city.getName(),
                "t", String.format("%.1f", w.temperature_2m()),
                "a", String.format("%.1f", w.apparent_temperature()),
                "h", String.valueOf(w.relative_humidity_2m()),
                "ws", String.format("%.1f", w.wind_speed_10m()),
                "desc", weatherDesc,
                "code", String.valueOf(w.weather_code())
        ));
        ChatResponse response = chatClient.prompt(prompt).call().chatResponse();

        String text = extractText(response);
        Advice advice = converter.convert(text);


        // 요청별 사용량/비용 로깅
        LlmUsage usage = extractUsage(response);
        BigDecimal estimatedUsd = llmCostEstimator.estimateUsd(usage.inputTokens, usage.outputTokens);

        log.info(
                "llm_usage feature=weather_advice model={} requestId={} " +
                        "inputTokens={} outputTokens={} totalTokens={} estimatedUsd={} city={}",
                usage.model(),
                usage.requestId(),
                usage.inputTokens(),
                usage.outputTokens(),
                usage.totalTokens(),
                estimatedUsd.toPlainString(),
                city.getName()
        );

        return advice;
    }

    private String extractText(ChatResponse response) {
        if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
            throw new IllegalStateException("LLM 응답이 비어 있습니다.");
        }

        var first = response.getResults().getFirst();
        AssistantMessage msg = first.getOutput();
        return Objects.requireNonNullElse(msg.getText(), "").trim();
    }


    private LlmUsage extractUsage(ChatResponse response) {
        try {
            if (response == null) {
                return LlmUsage.unknown();
            }

            ChatResponseMetadata md = response.getMetadata();
            if (md == null) {
                return LlmUsage.unknown();
            }

            String model = md.getModel();
            String requestId = md.getId();

            Usage usageObj = md.getUsage();
            long input = 0, output = 0, total = 0;

            if (usageObj != null) {
                input = usageObj.getPromptTokens();
                output = usageObj.getCompletionTokens();
                total = usageObj.getTotalTokens();
            }

            return new LlmUsage(model, requestId, input, output, total);
        } catch (Exception e) {
            return LlmUsage.unknown();
        }
    }

    private record LlmUsage(
            String model,
            String requestId,
            long inputTokens,
            long outputTokens,
            long totalTokens
    ) {
        static LlmUsage unknown() {
            return new LlmUsage( "unknown-model", "unknown-request", 0, 0, 0);
        }
    }

    public record Advice(String weatherSummary, String outfitSummary) { }
}