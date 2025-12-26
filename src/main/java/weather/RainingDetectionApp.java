package weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.*;

import java.time.Instant;
import java.util.Properties;

public class RainingDetectionApp {

    public static void main(String[] args) {

        ObjectMapper mapper = new ObjectMapper();

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "raining-detection-app");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());

        StreamsBuilder builder = new StreamsBuilder();

        KStream<String, String> weatherStream =
                builder.stream("weather_readings");

        KStream<String, String> rainingAlerts =
                weatherStream
                        .filter((key, value) -> {
                            try {
                                JsonNode root = mapper.readTree(value);
                                int humidity = root
                                        .get("weather")
                                        .get("humidity")
                                        .asInt();
                                return humidity > 70;
                            } catch (Exception e) {
                                return false;
                            }
                        })
                        .mapValues(value -> {
                            try {
                                JsonNode root = mapper.readTree(value);

                                long stationId = root.get("station_id").asLong();
                                long seq = root.get("s_no").asLong();
                                int humidity = root.get("weather").get("humidity").asInt();

                                return mapper.createObjectNode()
                                        .put("station_id", stationId)
                                        .put("sequence_number", seq)
                                        .put("humidity", humidity)
                                        .put("alert", "RAIN_DETECTED")
                                        .put("timestamp", Instant.now().getEpochSecond())
                                        .toString();

                            } catch (Exception e) {
                                return null;
                            }
                        })
                        .filter((k, v) -> v != null);

        rainingAlerts.to("raining_alerts");

        KafkaStreams streams = new KafkaStreams(builder.build(), props);

        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));

        streams.start();
    }
}
