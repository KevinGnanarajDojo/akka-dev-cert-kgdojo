package io.example.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GoogleWeatherService {

    private final HttpClient client;

    public GoogleWeatherService(HttpClient client) {
        this.client = client;
    }

    public GoogleWeatherService() {
        this(HttpClient.newHttpClient());
    }

    public String getWeatherForecast(LocalDateTime dateTime) {
        if (dateTime.isBefore(LocalDateTime.now())) {
            return "Requested date/time is in the past. Historical weather data is not available.";
        }
        if (dateTime.isAfter(LocalDateTime.now().plusDays(9))) {
            return "Requested date/time is too far in the future. Only a 10-day forecast is available.";
        }

        String apiKey = System.getenv("GOOGLE_API_KEY");
        double lat = 51.7509;
        double lon = 0.3398;
        int hours = 240;

        ObjectMapper mapper = new ObjectMapper();
        List<JsonNode> allForecasts = new ArrayList<>();
        String pageToken = null;

        try {
            do {
                String url = String.format(Locale.US,
                        "https://weather.googleapis.com/v1/forecast/hours:lookup?key=%s&location.latitude=%f&location.longitude=%f&hours=%d",
                        apiKey, lat, lon, hours);
                if (pageToken != null) {
                    url += "&pageToken=" + pageToken;
                }

                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                String responseBody = response.body();

                if (response.statusCode() == 200) {
                    if (responseBody == null || responseBody.trim().isEmpty()) {
                        return "Error: API returned 200 OK but with an empty response body.";
                    }
                    JsonNode root = mapper.readTree(responseBody);
                    JsonNode hourlyForecasts = root.path("forecastHours");
                    if (hourlyForecasts.isArray()) {
                        for (JsonNode forecast : hourlyForecasts) {
                            allForecasts.add(forecast);
                        }
                    }

                    JsonNode tokenNode = root.path("nextPageToken");
                    if (tokenNode.isMissingNode() || tokenNode.isNull() || tokenNode.asText().isEmpty()) {
                        pageToken = null;
                    } else {
                        pageToken = tokenNode.asText();
                    }
                } else {
                    return "Error: API returned status " + response.statusCode() + " Body: " + responseBody;
                }
            } while (pageToken != null);

            // Combine all pages into a single JSON structure to pass to the find method
            ObjectNode combinedRoot = mapper.createObjectNode();
            ArrayNode combinedForecasts = mapper.createArrayNode();
            combinedForecasts.addAll(allForecasts);
            combinedRoot.set("forecastHours", combinedForecasts);
            String combinedJson = mapper.writeValueAsString(combinedRoot);

            return findWeatherForDateTime(combinedJson, dateTime);

        } catch (Exception e) {
            e.printStackTrace();
            return "Error: Failed to fetch weather";
        }
    }

    private String findWeatherForDateTime(String jsonResponse, LocalDateTime target) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(jsonResponse);
            JsonNode hourlyForecasts = root.path("forecastHours");

            for (JsonNode forecast : hourlyForecasts) {
                String dateTimeStr = forecast.path("interval").path("startTime").asText();
                ZonedDateTime forecastDateTime = ZonedDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_ZONED_DATE_TIME);

                if (forecastDateTime.getYear() == target.getYear() &&
                    forecastDateTime.getMonthValue() == target.getMonthValue() &&
                    forecastDateTime.getDayOfMonth() == target.getDayOfMonth() &&
                    forecastDateTime.getHour() == target.getHour()) {
                    return getHourSummary(forecast);
                }
            }
            return "Date " + target + " is outside the available forecast window.";
        } catch (Exception e) {
            e.printStackTrace();
            return "Failed to parse JSON: " + e.getMessage();
        }
    }

    private String getHourSummary(JsonNode hour) {
        String condition = hour.path("weatherCondition").path("description").path("text").asText();
        int temp = (int) Math.round(hour.path("temperature").path("degrees").asDouble());
        int rainChance = hour.path("precipitation").path("probability").path("percent").asInt();
        int thunderstormChance = hour.path("thunderstormProbability").asInt();
        int windSpeed = (int) Math.round(hour.path("wind").path("speed").path("value").asDouble());


        return condition +
                ", Temp: " + temp + "°C" +
                ", Rain: " + rainChance + "%" +
                ", Thunder: " + thunderstormChance + "%" +
                ", Wind: " + windSpeed + " km/h";
    }
}