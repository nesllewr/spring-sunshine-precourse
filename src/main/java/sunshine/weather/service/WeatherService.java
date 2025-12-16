package sunshine.weather.service;

import org.springframework.stereotype.Service;
import sunshine.weather.model.City;
import sunshine.weather.dto.ForecastResponse;

@Service
public class WeatherService {
    private final OpenMeteo openMeteo;
    private final CityResolver cityResolver;
    private final LlmWeatherAdvisor weatherAdvisor;



    public WeatherService(OpenMeteo openMeteo, CityResolver cityResolver, LlmWeatherAdvisor weatherAdvisor) {
        this.openMeteo = openMeteo;
        this.cityResolver = cityResolver;
        this.weatherAdvisor = weatherAdvisor;
    }


    public String getWeatherSummary(String cityName) {
        City city = cityResolver.resolve(cityName);
        ForecastResponse.Current weather = openMeteo.fetchCurrent(city);
        return generateSummary(city, weather);
    }

    private String generateSummary(City city, ForecastResponse.Current weather) {
        LlmWeatherAdvisor.Advice advice = weatherAdvisor.advise(city, weather);

        // API 응답이 String이므로, 한 덩어리로 합쳐서 반환
        return advice.weatherSummary() + System.lineSeparator()
                + advice.outfitSummary();
    }
}