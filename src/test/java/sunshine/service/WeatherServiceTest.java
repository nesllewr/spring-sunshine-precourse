package sunshine.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import sunshine.weather.dto.ForecastResponse;
import sunshine.weather.model.City;
import sunshine.weather.service.CityResolver;
import sunshine.weather.service.OpenMeteo;
import sunshine.weather.service.WeatherService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class WeatherServiceTest {

    @Mock
    private OpenMeteo openMeteo;
    private WeatherService weatherService;
    private CityResolver cityResolver;


    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        weatherService = new WeatherService(openMeteo, cityResolver);
    }

    @Test
    @DisplayName("도시 입력에 대해 LLM(리졸버)이 반환한 좌표로 날씨 정보를 조회할 수 있다")
    void getWeatherSummaryForValidCity() {
        // given
        when(cityResolver.resolve("seoul"))
                .thenReturn(new City("Seoul", 37.5665, 126.9780));

        ForecastResponse.Current mockWeather = new ForecastResponse.Current(
                20.5, 19.0, 0, 65, 5.7
        );
        when(openMeteo.fetchCurrent(any(City.class))).thenReturn(mockWeather);

        // when
        String result = weatherService.getWeatherSummary("seoul");

        // then
        assertThat(result).contains("Seoul");
        assertThat(result).contains("20.5°C");
        assertThat(result).contains("맑음");
    }

    @Test
    @DisplayName("LLM(리졸버)이 좌표를 찾지 못하면 예외가 발생한다")
    void throwExceptionWhenResolverFails() {
        // given
        when(cityResolver.resolve("invalid"))
                .thenThrow(new IllegalArgumentException("도시 좌표를 확인할 수 없습니다: invalid"));

        // when & then
        assertThatThrownBy(() -> weatherService.getWeatherSummary("invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("도시 좌표를 확인할 수 없습니다");
    }
}