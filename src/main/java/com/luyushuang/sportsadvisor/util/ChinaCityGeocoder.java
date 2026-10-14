package com.luyushuang.sportsadvisor.util;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.core.ParameterizedTypeReference;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChinaCityGeocoder {

    private final RestClient restClient;
    private final Map<String, double[]> cache = new ConcurrentHashMap<>();

    public ChinaCityGeocoder(RestClient weatherRestClient) {
        this.restClient = weatherRestClient;
    }

    public double[] resolve(String city) {
        return cache.computeIfAbsent(city, this::fetchCoordinates);
    }

    private double[] fetchCoordinates(String city) {
        String url = "https://geocoding-api.open-meteo.com/v1/search"
                + "?name=" + city
                + "&count=1"
                + "&language=zh"
                + "&countryCode=CN";

        try {
            Map<String, Object> response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
            if (results == null || results.isEmpty()) {
                throw new IllegalArgumentException("未找到中国城市：" + city);
            }

            Map<String, Object> first = results.get(0);
            double lat = ((Number) first.get("latitude")).doubleValue();
            double lon = ((Number) first.get("longitude")).doubleValue();
            return new double[]{lat, lon};

        } catch (Exception e) {
            throw new RuntimeException("解析城市坐标失败：" + city, e);
        }
    }
}