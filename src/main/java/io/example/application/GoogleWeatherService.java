package io.example.application;

import akka.javasdk.annotations.FunctionTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class GoogleWeatherService {

    private final HttpClient client;

    // Constructor Injection: Allows us to pass a Mock client in tests
    // or use a real one in production.
    public GoogleWeatherService(HttpClient client) {
        this.client = client;
    }

    // Default constructor for normal use
    public GoogleWeatherService() {
        this(HttpClient.newHttpClient());
    }

    @FunctionTool(description = "Queries the weather conditions...")
    public String getWeatherForecast(String timeSlotId) {
        // Logic to parse timeSlotId would go here
        // For now, we simulate the logic:
        LocalDate targetDate = LocalDate.now().plusDays(10);

        // Note: In a real app, pass the API key in via constructor too
        String apiKey = System.getenv("GOOGLE_API_KEY");
        double lat = 51.7509;
        double lon = 0.3398;

        String url = String.format(
                "https://weather.googleapis.com/v1/forecast/days:lookup?location.latitude=%f&location.longitude=%f&days=10&key=%s",
                lat, lon, apiKey
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        try {
            // Use the class-level client
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return findWeatherForDate(response.body(), targetDate);
            } else {
                return "Error: API returned status " + response.statusCode();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Error: Failed to fetch weather";
        }
    }

    // Changed return type from void to String
    private String findWeatherForDate(String jsonResponse, LocalDate target) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(jsonResponse);
            JsonNode forecastList = root.path("forecastDays");

            for (JsonNode day : forecastList) {
                String startTime = day.path("interval").path("startTime").asText();
                // Handle potential parsing errors safely
                LocalDate forecastDate = LocalDate.parse(startTime, DateTimeFormatter.ISO_DATE_TIME);

                if (forecastDate.equals(target)) {
                    return getDaySummary(day);
                }
            }
            return "Date " + target + " is outside the available 10-day forecast window.";
        } catch (Exception e) {
            return "Failed to parse JSON: " + e.getMessage();
        }
    }

    private String getDaySummary(JsonNode day) {
        String condition = day.path("daytimeForecast").path("weatherCondition").path("description").asText();
        int highTemp = (int) Math.round(day.path("maxTemperature").path("value").asDouble());
        int lowTemp = (int) Math.round(day.path("minTemperature").path("value").asDouble());
        int rainChance = day.path("daytimeForecast").path("precipitation").path("probability").asInt();

        return String.format("%s, High: %d°C, Low: %d°C, Rain: %d%%",
                condition, highTemp, lowTemp, rainChance);
    }
}