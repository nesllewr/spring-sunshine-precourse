package sunshine.weather.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;
import sunshine.weather.dto.ForecastResponse;
import sunshine.weather.model.City;
import sunshine.weather.model.WeatherCode;

import java.util.Map;

@Component
public class LlmWeatherAdvisor {

    private final ChatClient chatClient;

    public LlmWeatherAdvisor(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
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

        String text = chatClient.prompt(prompt).call().content();
        Advice advice = converter.convert(text);

        if (advice == null || isBlank(advice.weatherSummary()) || isBlank(advice.outfitSummary())) {
            throw new IllegalStateException("LLM 응답을 파싱했지만 필수 필드가 비어 있습니다.");
        }

        return advice;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public record Advice(String weatherSummary, String outfitSummary) { }
}