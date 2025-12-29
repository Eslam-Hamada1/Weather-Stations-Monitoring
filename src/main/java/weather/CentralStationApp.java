package weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.sql.*;
import java.time.Duration;
import java.util.*;

public class CentralStationApp {

    private static final String TOPIC = "weather_readings";
    private static final int BATCH_SIZE = 300;

    // private static final String DB_URL = "jdbc:postgresql://localhost:5432/weatherdb";
    // private static final String DB_USER = "postgres";
    // private static final String DB_PASSWORD = "postgres";

    private static final String DB_URL = System.getenv("DB_URL") != null ? System.getenv("DB_URL") : "jdbc:postgresql://localhost:5432/weatherdb";
    private static final String DB_USER = System.getenv("DB_USER") != null ? System.getenv("DB_USER") : "postgres";
    private static final String DB_PASSWORD = System.getenv("DB_PASSWORD") != null ? System.getenv("DB_PASSWORD") : "postgres";

    public static void main(String[] args) throws Exception {

        Properties props = new Properties();
        // props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        String bootstrap = System.getenv("KAFKA_BOOTSTRAP_SERVERS");
        if (bootstrap == null || bootstrap.isEmpty()) {
        bootstrap = "localhost:9092";
        }
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "central-station-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false"); 

        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList(TOPIC));

        Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
        conn.setAutoCommit(false);

        String insertSQL =
                "INSERT INTO weather_readings " +
                "(station_id, sequence_number, battery_status, timestamp, humidity, temperature, wind_speed) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";

        PreparedStatement stmt = conn.prepareStatement(insertSQL);

        ObjectMapper mapper = new ObjectMapper();
        int batchCount = 0;

        System.out.println("Central Station started...");

        while (true) {
            ConsumerRecords<String, String> records =
                    consumer.poll(Duration.ofMillis(1000));

            for (ConsumerRecord<String, String> record : records) {

                JsonNode root = mapper.readTree(record.value());

                stmt.setLong(1, root.get("station_id").asLong());
                stmt.setLong(2, root.get("s_no").asLong());
                stmt.setString(3, root.get("battery_status").asText());
                stmt.setLong(4, root.get("status_timestamp").asLong());

                JsonNode weather = root.get("weather");
                stmt.setInt(5, weather.get("humidity").asInt());
                stmt.setInt(6, weather.get("temperature").asInt());
                stmt.setInt(7, weather.get("wind_speed").asInt());

                stmt.addBatch();
                batchCount++;

                if (batchCount >= BATCH_SIZE) {
                    stmt.executeBatch();
                    conn.commit();
                    consumer.commitSync();

                    System.out.println("Inserted batch of " + batchCount);
                    batchCount = 0;
                }
            }
        }
    }
}
