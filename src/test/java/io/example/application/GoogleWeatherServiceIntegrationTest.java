package io.example.application;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class GoogleWeatherServiceIntegrationTest {

    @Test
    void testGetWeatherForecastLiveIntegration(){
        GoogleWeatherService service = new GoogleWeatherService();

        LocalDateTime time = LocalDateTime.of(2025,12,26,7,0);
        System.out.println("Starting live API Test to find weather for this date:" + time);
        String result = service.getWeatherForecast(time);
        System.out.println("Result received:" + result);

        assertNotNull(result, "Result should not be null");
        assertFalse(result.isEmpty(), "Result should not be empty");
    }

    //TODO: Actually make tests mocking the service

}
