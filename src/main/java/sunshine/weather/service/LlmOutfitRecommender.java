package sunshine.weather.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Component;
import sunshine.weather.dto.ForecastResponse;
import sunshine.weather.model.City;
import sunshine.weather.model.WeatherCode;

import java.util.Map;

@Component
public class LlmOutfitRecommender {

    private final ChatClient chatClient;

    public LlmOutfitRecommender(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public String recommend(City city, ForecastResponse.Current w) {
        String weatherDesc = WeatherCode.getDescription(w.weather_code());

        String userMessage = """
                너는 날씨 기반 복장 추천 스타일리스트야.
                아래 입력(도시, 현재 날씨 수치)을 기반으로 오늘 입기 좋은 복장을 한국어로 추천해줘.

                요구사항:
                - 2~4문장으로 작성
                - 기온/체감온도/바람/습도/강수 가능성을 고려해서 이유를 짧게 포함
                - 과장하지 말고, 애매하면 "가벼운 겉옷" 같이 안전한 표현 사용
                - 특정 브랜드 언급 금지
                - 우산/방수 같은 준비물도 필요하면 포함

                [도시]
                - {cityName}

                [현재 날씨]
                - 기온: {t}°C
                - 체감: {a}°C
                - 습도: {h}%
                - 풍속: {w}m/s
                - 상태: {desc} (code={code})
                """;

        var promptTemplate = new PromptTemplate(userMessage);
        var prompt = promptTemplate.create(Map.of(
                "cityName", city.getName(),
                "t", String.format("%.1f", w.temperature_2m()),
                "a", String.format("%.1f", w.apparent_temperature()),
                "h", String.valueOf(w.relative_humidity_2m()),
                "w", String.format("%.1f", w.wind_speed_10m()),
                "desc", weatherDesc,
                "code", String.valueOf(w.weather_code())
        ));

        return chatClient.prompt(prompt).call().content();
    }
}
