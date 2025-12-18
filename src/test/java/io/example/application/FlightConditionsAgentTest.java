package io.example.application;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import io.example.application.FlightConditionsAgent;
import io.example.application.GoogleWeatherService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class FlightConditionsAgentTest extends TestKitSupport {

    private final TestModelProvider flightConditionsModel = new TestModelProvider();

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT
                .withAdditionalConfig("akka.javasdk.agent.openai.api-key = n/a")
                .withModelProvider(FlightConditionsAgent.class, flightConditionsModel);
    }

    @Test
    public void safeConditionsReportIsTrue() {
        // This is exactly what the model should return as JSON for ConditionsReport
        flightConditionsModel.fixedResponse("""
      {
        "timeSlotId": "2025-12-25T10:00:00",
        "meetsRequirements": true
      }
      """);

        var sessionId = UUID.randomUUID().toString();
        var timeSlotId = "2025-12-25T10:00:00";

        var report = componentClient
                .forAgent()
                .inSession(sessionId)
                .method(FlightConditionsAgent::query)
                .invoke(timeSlotId);

        assertThat(report.timeSlotId()).isEqualTo(timeSlotId);
        assertThat(report.meetsRequirements()).isTrue();
    }

    @Test
    void getWeatherForecastApiTest(){
        GoogleWeatherService service = new GoogleWeatherService();

        LocalDateTime time = LocalDateTime.of(2025,12,26,7,0);
        System.out.println("Starting live API Test to find weather for this date:" + time);
        String result = service.getWeatherForecast(time);
        System.out.println("Result received:" + result);

        assertNotNull(result, "Result should not be null");
        assertFalse(result.isEmpty(), "Result should not be empty");
    }
}