package sunshine.weather.service;

import org.springframework.stereotype.Service;
import sunshine.weather.model.City;
import sunshine.weather.dto.ForecastResponse;
import sunshine.weather.model.WeatherCode;
import java.util.Map;

@Service
public class WeatherService {
    private final OpenMeteo openMeteo;
    private final CityResolver cityResolver;

    public WeatherService(OpenMeteo openMeteo, CityResolver cityResolver) {
        this.openMeteo = openMeteo;
        this.cityResolver = cityResolver;
    }

    public String getWeatherSummary(String cityName) {
        City city = cityResolver.resolve(cityName);
        ForecastResponse.Current weather = openMeteo.fetchCurrent(city);
        return generateSummary(city, weather);
    }

    private String generateSummary(City city, ForecastResponse.Current weather) {
        return String.format(
            "현재 %s의 기온은 %.1f°C이며, 체감온도는 %.1f°C입니다. 습도는 %d%%이고, 풍속은 %.1fm/s입니다. 날씨는 %s입니다.",
            city.getName(),
            weather.temperature_2m(),
            weather.apparent_temperature(),
            weather.relative_humidity_2m(),
            weather.wind_speed_10m(),
            WeatherCode.getDescription(weather.weather_code())
        );
    }
}