package sunshine.weather.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;
import sunshine.weather.model.City;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// ... existing code ...

@Component
public class LlmCityResolver implements CityResolver {

    private final ChatClient chatClient;

    /**
     * 간단 in-memory 캐시 (원하면 Spring Cache로 교체 가능)
     */
    private final Map<String, City> cache = new ConcurrentHashMap<>();

    public LlmCityResolver(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public City resolve(String inputCityName) {
        if (inputCityName == null || inputCityName.isBlank()) {
            throw new IllegalArgumentException("도시 이름은 비어 있을 수 없습니다.");
        }

        String key = normalize(inputCityName);
        City cached = cache.get(key);
        if (cached != null) return cached;

        var converter = new BeanOutputConverter<>(CityGeo.class);
        var format = converter.getFormat();

        var userMessage = """
                너는 지오코딩 도우미야.
                사용자가 입력한 도시/지역 이름을 보고, 해당 위치를 대표하는 좌표(위도/경도)를 반환해.

                반드시 아래 형식 지시(format)를 따르고, 다른 텍스트는 절대 포함하지 마.
                모르면 임의로 만들지 말고 latitude/longitude를 null로 반환해.

                입력: "{city}"

                {format}
                """;

        var promptTemplate = new PromptTemplate(userMessage);
        var prompt = promptTemplate.create(Map.of(
                "city", inputCityName,
                "format", format
        ));

        // ✅ /actors 예제와 동일한 패턴: call().content()
        String text = chatClient.prompt(prompt).call().content();

        CityGeo geo = converter.convert(text);
        City city = validateAndToCity(geo, inputCityName);

        cache.put(key, city);
        return city;
    }

    private String normalize(String s) {
        return s.trim().toLowerCase(Locale.ROOT);
    }

    private City validateAndToCity(CityGeo geo, String originalInput) {
        if (geo == null) {
            throw new IllegalArgumentException("도시 좌표를 확인할 수 없습니다: " + originalInput);
        }
        if (geo.latitude() == null || geo.longitude() == null) {
            throw new IllegalArgumentException("도시 좌표를 확인할 수 없습니다: " + originalInput);
        }

        double lat = geo.latitude();
        double lon = geo.longitude();

        if (Double.isNaN(lat) || Double.isNaN(lon) || Double.isInfinite(lat) || Double.isInfinite(lon)) {
            throw new IllegalArgumentException("도시 좌표 형식이 올바르지 않습니다: " + originalInput);
        }
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            throw new IllegalArgumentException("도시 좌표 범위를 벗어났습니다: " + originalInput);
        }

        String name = (geo.name() == null || geo.name().isBlank()) ? originalInput.trim() : geo.name().trim();
        return new City(name, lat, lon);
    }

    /**
     * LLM 구조화 출력용 DTO (BeanOutputConverter 대상)
     */
    public record CityGeo(String name, Double latitude, Double longitude) {}
}