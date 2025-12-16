package io.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/*
 * The flight conditions agent is responsible for making a determination about the flight
 * conditions for a given day and time. You will need to clearly define the success criteria
 * for the report and instruct the agent (in the system prompt) about the schema of
 * the results it must return (the ConditionsReport).
 *
 * Also be sure to provide clear instructions on how and when tools should be invoked
 * in order to generate results.
 *
 * Flight conditions criteria don't need to be exhaustive, but you should supply the
 * criteria so that an agent does not need to make an external HTTP call to query
 * the condition limits.
 */

@Component(id = "flight-conditions-agent")
public class FlightConditionsAgent extends Agent {

    record ConditionsReport(String timeSlotId, Boolean meetsRequirements) {
    }

    private static final String SYSTEM_MESSAGE = """
            You are an agent responsible for evaluating flight conditions...
            """.stripIndent();

    public Effect<ConditionsReport> query(String timeSlotId) {
        return effects()
                .model(ModelProvider.googleAiGemini().withApiKey(System.getenv("GOOGLE_API_KEY")))
                .systemMessage(SYSTEM_MESSAGE)
                .userMessage("Validate the conditions...")
                .responseAs(ConditionsReport.class)
                .thenReply();
    }

    /*
     * You can choose to hard code the weather conditions for specific days or you
     * can actually
     * communicate with an external weather API. You should be able to get both
     * suitable weather
     * conditions and poor weather conditions from this tool function for testing.
     */
    // TODO: Change back to private function after testing
    @FunctionTool(description = "Queries the weather conditions as they are forecasted based on the time slot ID of the training session booking")
    public String getWeatherForecast(String timeSlotId) {

        double lat = 51.7509;
        double lon = 0.3398;
        LocalDate targetDate = LocalDate.now().plusDays(10); //TODO: Parse Timeslot ID to find this
        String apiKey = System.getenv("GOOGLE_API_KEY");
        String url = String.format(
                "https://weather.googleapis.com/v1/forecast/days:lookup?location.latitude=%f&location.longitude=%f&days=10&key=%s",
                lat, lon, apiKey
        );

        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET() // GET is default, but good practice to be explicit
                .build();

        try {
            // 3. Send Request
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                // 4. Parse and Filter
                findWeatherForDate(response.body(), targetDate);
            } else {
                System.out.println("API Error: " + response.statusCode());
                System.out.println(response.body());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "";
    }

    private static void findWeatherForDate(String jsonResponse, LocalDate target) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(jsonResponse);
            JsonNode forecastList = root.path("forecastDays");

            boolean found = false;

            for (JsonNode day : forecastList) {
                // Extract the date from "interval.startTime" (Format: "2023-10-25T07:00:00Z")
                String startTime = day.path("interval").path("startTime").asText();
                LocalDate forecastDate = LocalDate.parse(startTime, DateTimeFormatter.ISO_DATE_TIME);

                // Check for match
                if (forecastDate.equals(target)) {
                    found = true;
                    String summary = getDaySummary(day);
                    System.out.println("Summary for " + target + ": " + summary);
                    break;
                }
            }

            if (!found) {
                System.out.println("Date " + target + " is outside the available 10-day forecast window.");
            }

        } catch (Exception e) {
            System.err.println("Failed to parse JSON: " + e.getMessage());
        }
    }
    private static String getDaySummary(JsonNode day) {
        // 1. Extract Description (e.g., "Partly cloudy")
        // We prioritize the 'daytime' forecast for the general condition
        String condition = day.path("daytimeForecast").path("weatherCondition").path("description").asText();

        // 2. Extract Temperatures (Rounded to nearest whole number for cleanliness)
        int highTemp = (int) Math.round(day.path("maxTemperature").path("value").asDouble());
        int lowTemp = (int) Math.round(day.path("minTemperature").path("value").asDouble());

        // 3. Extract Rain Probability
        int rainChance = day.path("daytimeForecast").path("precipitation").path("probability").asInt();

        // 4. Return formatted string
        return String.format("%s, High: %d°C, Low: %d°C, Rain: %d%%",
                condition, highTemp, lowTemp, rainChance);
    }
}
