package weather;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Random;

public class WeatherStationA {

    private static final Random random = new Random();
    private static final ObjectMapper mapper = new ObjectMapper();

    private static long sequenceNumber = 1;

    public static void main(String[] args) throws Exception {

        long stationId = args.length > 0 ? Long.parseLong(args[0]) : 1;

        while (true) {

            // 10% drop
            if (random.nextInt(100) < 10) {
                Thread.sleep(1000);
                continue;
            }

            String batteryStatus = randomBatteryStatus();
            Weather weather = randomWeather();

            WeatherReading reading = new WeatherReading(
                    stationId,
                    sequenceNumber++,
                    batteryStatus,
                    Instant.now().getEpochSecond(),
                    weather
            );

            String json = mapper.writeValueAsString(reading);
            System.out.println(json);

            Thread.sleep(1000);
        }
    }

    private static String randomBatteryStatus() {
        int r = random.nextInt(100);
        if (r < 30) return "low";
        else if (r < 70) return "medium";
        else return "high";
    }

    private static Weather randomWeather() {
        int humidity = random.nextInt(101);
        int temperature = 30 + random.nextInt(91);
        int windSpeed = random.nextInt(41);

        return new Weather(humidity, temperature, windSpeed);
    }
}
