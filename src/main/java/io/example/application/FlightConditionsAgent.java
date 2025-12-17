package io.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;

import java.time.LocalDateTime;
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
            You are a flight conditions agent responsible for evaluating weather conditions to determine if it is safe to fly a small plane.
            Your task is to assess the weather for a given time slot and decide if it meets the safety requirements.

            You MUST use the 'getWeatherForecast' tool to obtain the weather data for the specified 'timeSlotId'.

            The flight conditions are considered safe ONLY IF ALL of the following criteria are met:
            1.  Wind speed is less than 20 km/h.
            2.  The chance of rain is less than 30%.
            3.  The chance of a thunderstorm is 0%.

            Based on the data from the weather tool, you will return a 'ConditionsReport'.
            - Set 'meetsRequirements' to 'true' if and only if all the above safety criteria are satisfied.
            - Set 'meetsRequirements' to 'false' if any of the criteria are not met.
            - The 'timeSlotId' in the report must match the one provided in the user message.
            """.stripIndent();

    public Effect<ConditionsReport> query(String timeSlotId) {
        return effects()
                .model(ModelProvider.googleAiGemini().withApiKey(System.getenv("GOOGLE_API_KEY")))
                .systemMessage(SYSTEM_MESSAGE)
                .userMessage("Validate the conditions for time slot " + timeSlotId)
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
    @FunctionTool(description = "Queries the weather conditions as they are forecasted based on the time slot ID of the training session booking")
    private String getWeatherForecast(String timeSlotId) {
        try {
            // The timeSlotId is expected to be in ISO_LOCAL_DATE_TIME format (e.g., "2025-12-25T10:00:00")
            LocalDateTime dateTime = LocalDateTime.parse(timeSlotId, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            GoogleWeatherService weatherService = new GoogleWeatherService();
            return weatherService.getWeatherForecast(dateTime);
        } catch (Exception e) {
            e.printStackTrace();
            return "Error parsing timeSlotId: " + e.getMessage();
        }
    }
}