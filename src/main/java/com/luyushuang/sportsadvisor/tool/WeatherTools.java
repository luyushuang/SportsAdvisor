package com.luyushuang.sportsadvisor.tool;

import com.luyushuang.sportsadvisor.util.ChinaCityGeocoder;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.core.ParameterizedTypeReference;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Component
public class WeatherTools {

    private static final int MAX_FORECAST_DAYS = 16;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final RestClient restClient;
    private final ChinaCityGeocoder geocoder;

    public WeatherTools(RestClient weatherRestClient, ChinaCityGeocoder geocoder) {
        this.restClient = weatherRestClient;
        this.geocoder = geocoder;
    }

    @Tool(description = "获取中国城市指定日期的天气与空气质量数据。返回温度范围、降水量、天气状况和空气质量指数（美国AQI标准）。仅支持未来16天内的日期。")
    public String getWeatherAndAqi(
            @ToolParam(description = "中国城市名称，例如：北京、上海、深圳") String city,
            @ToolParam(description = "日期，格式 yyyy-MM-dd") String date) {

        // ---- 日期校验 ----
        LocalDate targetDate;
        try {
            targetDate = LocalDate.parse(date, DATE_FMT);
        } catch (Exception e) {
            return "错误：日期格式不正确，请使用 yyyy-MM-dd";
        }

        LocalDate today = LocalDate.now();
        long daysAhead = ChronoUnit.DAYS.between(today, targetDate);
        if (daysAhead < 0) {
            return "错误：无法获取过去日期的天气预测。请提供今天或未来的日期。";
        }
        if (daysAhead > MAX_FORECAST_DAYS) {
            return String.format("错误：最多支持未来 %d 天的预报，当前请求 %d 天后。", MAX_FORECAST_DAYS, daysAhead);
        }

        // ---- 城市坐标解析 ----
        double[] coords;
        try {
            coords = geocoder.resolve(city);
        } catch (Exception e) {
            return "错误：无法解析城市坐标——" + e.getMessage();
        }

        // ---- 获取天气与空气质量 ----
        try {
            Map<String, Object> weather = fetchWeather(coords, date);
            Map<String, Object> aqi = fetchAirQuality(coords, date);

            return formatResult(city, date, weather, aqi);

        } catch (Exception e) {
            return "错误：获取天气数据失败——" + e.getMessage();
        }
    }

    private Map<String, Object> fetchWeather(double[] coords, String date) {
        String url = String.format(
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                        + "&daily=temperature_2m_max,temperature_2m_min,precipitation_sum,weather_code"
                        + "&timezone=auto&start_date=%s&end_date=%s",
                coords[0], coords[1], date, date);

        return restClient.get().uri(url).retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    private Map<String, Object> fetchAirQuality(double[] coords, String date) {
        String url = String.format(
                "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=%.4f&longitude=%.4f"
                        + "&hourly=us_aqi&timezone=auto&start_date=%s&end_date=%s",
                coords[0], coords[1], date, date);

        return restClient.get().uri(url).retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    private String formatResult(String city, String date,
                                Map<String, Object> weather,
                                Map<String, Object> aqi) {
        // 解析天气
        Map<String, Object> daily = (Map<String, Object>) weather.get("daily");
        double tempMax = ((Number) ((List<Object>) daily.get("temperature_2m_max")).get(0)).doubleValue();
        double tempMin = ((Number) ((List<Object>) daily.get("temperature_2m_min")).get(0)).doubleValue();
        double precipitation = ((Number) ((List<Object>) daily.get("precipitation_sum")).get(0)).doubleValue();
        int weatherCode = ((Number) ((List<Object>) daily.get("weather_code")).get(0)).intValue();

        // 解析 AQI（取当天所有小时的最大值，代表当日最差空气质量）
        Map<String, Object> hourly = (Map<String, Object>) aqi.get("hourly");
        List<Object> aqiValues = (List<Object>) hourly.get("us_aqi");
        int maxAqi = aqiValues.stream()
                .filter(v -> v instanceof Number)
                .mapToInt(v -> ((Number) v).intValue())
                .max().orElse(-1);

        return String.format(
                "城市：%s | 日期：%s\n" +
                        "天气：%s\n" +
                        "温度：%.1f°C ~ %.1f°C\n" +
                        "降水量：%.1f mm\n" +
                        "空气质量指数(US AQI)：%d",
                city, date,
                describeWeatherCode(weatherCode),
                tempMin, tempMax,
                precipitation,
                maxAqi);
    }

    private String describeWeatherCode(int code) {
        // 参考 Open-Meteo WMO 天气代码
        if (code == 0) return "晴";
        if (code <= 3) return "多云";
        if (code <= 48) return "雾";
        if (code <= 57) return "毛毛雨";
        if (code <= 67) return "雨";
        if (code <= 77) return "雪";
        if (code <= 82) return "阵雨";
        if (code <= 86) return "阵雪";
        if (code <= 99) return "雷暴";
        return "未知";
    }
}
