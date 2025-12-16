package sunshine.weather.service;

import sunshine.weather.model.City;

public interface CityResolver {
    City resolve(String inputCityName);
}
