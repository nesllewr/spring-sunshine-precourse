package sunshine.weather.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import sunshine.weather.dto.ForecastResponse;
import sunshine.weather.model.City;
import sunshine.weather.model.WeatherCode;

@Service
public class WeatherService {
    private final OpenMeteo openMeteo;
    private final CityResolver cityResolver;
    private final LlmWeatherAdvisor weatherAdvisor;
    private final boolean llmEnabled;

    public WeatherService(
            OpenMeteo openMeteo,
            CityResolver cityResolver,
            LlmWeatherAdvisor weatherAdvisor,
            @Value("${sunshine.llm.enabled:false}") boolean llmEnabled
    ) {
        this.openMeteo = openMeteo;
        this.cityResolver = cityResolver;
        this.weatherAdvisor = weatherAdvisor;
        this.llmEnabled = llmEnabled;
    }

    public String getWeatherSummary(String cityName) {
        City city = cityResolver.resolve(cityName);
        ForecastResponse.Current weather = openMeteo.fetchCurrent(city);
        return generateSummary(city, weather);
    }

    private String generateSummary(City city, ForecastResponse.Current weather) {
        if (!llmEnabled) {
            return generateTemplateSummary(city, weather);
        }

        LlmWeatherAdvisor.Advice advice = weatherAdvisor.advise(city, weather);

        // API 응답이 String이므로, 한 덩어리로 합쳐서 반환
        return advice.weatherSummary() + System.lineSeparator()
                + advice.outfitSummary();
    }

    private String generateTemplateSummary(City city, ForecastResponse.Current w) {
        String desc = WeatherCode.getDescription(w.weather_code());

        String weatherSummary = String.format(
                "%s 현재 날씨는 %s입니다. 기온 %.1f°C(체감 %.1f°C), 습도 %d%%, 풍속 %.1fm/s 입니다.",
                city.getName(),
                desc,
                w.temperature_2m(),
                w.apparent_temperature(),
                w.relative_humidity_2m(),
                w.wind_speed_10m()
        );

        // 아주 단순한 규칙 기반 옷차림 템플릿(필요 시 규칙 더 추가 가능)
        String outfitSummary;
        double t = w.apparent_temperature();
        if (t <= 5) {
            outfitSummary = "두꺼운 외투(패딩/코트)와 목도리 등 보온을 권장해요. 바람이 있으면 체감이 더 낮을 수 있어요.";
        } else if (t <= 15) {
            outfitSummary = "가벼운 자켓/가디건 레이어드를 권장해요. 바람이 있으면 얇은 바람막이가 도움이 될 수 있어요.";
        } else if (t <= 23) {
            outfitSummary = "긴팔 또는 얇은 겉옷 정도가 무난해요. 실내외 온도 차에 대비해 가벼운 겉옷을 챙기면 좋아요.";
        } else {
            outfitSummary = "가볍고 통풍이 좋은 옷차림을 권장해요. 수분 보충과 자외선 대비도 함께 챙겨요.";
        }

        return weatherSummary + System.lineSeparator() + outfitSummary;
    }
}