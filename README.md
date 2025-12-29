# Weather Stations Monitoring System

A distributed weather monitoring system built with Java, Apache Kafka, and PostgreSQL, deployed on Kubernetes.

## Architecture

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│ Weather Station │────▶│      Kafka      │────▶│ Central Station │
│   (10 pods)     │     │   + Zookeeper   │     │    (1 pod)      │
└─────────────────┘     └─────────────────┘     └────────┬────────┘
                               │                         │
                               │                         ▼
                               │                 ┌───────────────┐
                               │                 │  PostgreSQL   │
                               ▼                 │  (Database)   │
                       ┌───────────────┐         └───────────────┘
                       │    Raining    │
                       │   Detection   │
                       └───────────────┘
```

## Components

| Component | Description |
|-----------|-------------|
| **Weather Station** | 10 pods generating simulated weather data |
| **Kafka + Zookeeper** | Message broker for data streaming |
| **Central Station** | Consumes weather data and stores in PostgreSQL |
| **Raining Detection** | Kafka Streams app for rain alerts |
| **PostgreSQL** | Persistent storage for weather readings |

---

## Prerequisites

- **Docker Desktop** with Kubernetes enabled
- **Java 17** (for building)
- **Maven** (for building)
- **kubectl** CLI

### Enable Kubernetes in Docker Desktop

1. Open Docker Desktop
2. Go to **Settings** → **Kubernetes**
3. Check **Enable Kubernetes**
4. Click **Apply & Restart**

---

## Deployment Instructions

### Step 1: Clone and Build the Project

```powershell
# Navigate to project directory
cd d:\repos\Weather-Stations-Monitoring

# Build the project (creates fat JAR with all dependencies)
mvn clean package -DskipTests
```

### Step 2: Build Docker Images

```powershell
# Build Weather Station image
docker build -t weather-station:1.0 -f Dockerfile.weather-station .

# Build Central Station image
docker build -t central-station:1.0 -f Dockerfile.central-station .

# Build Raining Detection image
docker build -t raining-detection:1.0 -f Dockerfile.raining-detection .
```

### Step 3: Verify Docker Images

```powershell
docker images | findstr "weather-station central-station raining-detection"
```

### Step 4: Deploy to Kubernetes

Deploy components in order (infrastructure first, then applications):

```powershell
# 1. Deploy PostgreSQL (with secret and persistent volume)
kubectl apply -f k8s/postgres-secret.yaml
kubectl apply -f k8s/postgres.yaml

# 2. Deploy Zookeeper
kubectl apply -f k8s/zookeeper.yaml

# 3. Deploy Kafka
kubectl apply -f k8s/kafka.yaml

# 4. Wait for infrastructure to be ready (about 30-60 seconds)
kubectl get pods -w
# Press Ctrl+C when all pods show "Running"

# 5. Create the database table
kubectl exec -it postgres-0 -- psql -U postgres -d weatherdb -c "CREATE TABLE IF NOT EXISTS weather_readings (id SERIAL PRIMARY KEY, station_id BIGINT NOT NULL, sequence_number BIGINT NOT NULL, battery_status VARCHAR(50), timestamp BIGINT, humidity INT, temperature INT, wind_speed INT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);"

# 6. Deploy Central Station
kubectl apply -f k8s/central-station.yaml

# 7. Deploy Raining Detection
kubectl apply -f k8s/raining-detection.yaml

# 8. Deploy Weather Stations (10 pods)
kubectl apply -f k8s/weather-station.yaml
```

### One-Command Deployment (After Images are Built)

```powershell
# Deploy all infrastructure
kubectl apply -f k8s/postgres-secret.yaml -f k8s/postgres.yaml -f k8s/zookeeper.yaml -f k8s/kafka.yaml

# Wait 60 seconds for infrastructure
Start-Sleep -Seconds 60

# Create database table
kubectl exec -it postgres-0 -- psql -U postgres -d weatherdb -c "CREATE TABLE IF NOT EXISTS weather_readings (id SERIAL PRIMARY KEY, station_id BIGINT NOT NULL, sequence_number BIGINT NOT NULL, battery_status VARCHAR(50), timestamp BIGINT, humidity INT, temperature INT, wind_speed INT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);"

# Deploy applications
kubectl apply -f k8s/central-station.yaml -f k8s/raining-detection.yaml -f k8s/weather-station.yaml
```

---

## Verification Commands

### Check All Pods Are Running

```powershell
kubectl get pods
```

Expected output:
```
NAME                                 READY   STATUS    RESTARTS   AGE
central-station-xxxxx                1/1     Running   0          5m
kafka-xxxxx                          1/1     Running   0          10m
postgres-0                           1/1     Running   0          10m
raining-detection-xxxxx              1/1     Running   0          5m
weather-station-0                    1/1     Running   0          5m
weather-station-1                    1/1     Running   0          5m
...
weather-station-9                    1/1     Running   0          5m
zookeeper-xxxxx                      1/1     Running   0          10m
```

### Check Services

```powershell
kubectl get services
```

### Check Database Records

```powershell
# Total record count
kubectl exec -it postgres-0 -- psql -U postgres -d weatherdb -c "SELECT COUNT(*) FROM weather_readings;"

# Records per station
kubectl exec -it postgres-0 -- psql -U postgres -d weatherdb -c "SELECT station_id, COUNT(*) as readings FROM weather_readings GROUP BY station_id ORDER BY station_id;"

# View recent records
kubectl exec -it postgres-0 -- psql -U postgres -d weatherdb -c "SELECT * FROM weather_readings ORDER BY id DESC LIMIT 10;"
```

### Check Central Station Logs

```powershell
kubectl logs -l app=central-station --tail=20
```

### Check Weather Station Logs

```powershell
kubectl logs weather-station-0 --tail=10
```

---

## Cleanup / Teardown

### Delete All Deployments

```powershell
# Delete applications
kubectl delete -f k8s/weather-station.yaml
kubectl delete -f k8s/central-station.yaml
kubectl delete -f k8s/raining-detection.yaml

# Delete infrastructure
kubectl delete -f k8s/kafka.yaml
kubectl delete -f k8s/zookeeper.yaml
kubectl delete -f k8s/postgres.yaml
kubectl delete -f k8s/postgres-secret.yaml

# Delete persistent volume claims (removes data)
kubectl delete pvc --all
```

### Delete Everything at Once

```powershell
kubectl delete -f k8s/
```

---

## Troubleshooting

### Pods Stuck in "Pending" or "ContainerCreating"

```powershell
# Check pod events
kubectl describe pod <pod-name>

# Check if images exist
docker images
```

### Pods in "CrashLoopBackOff"

```powershell
# Check logs
kubectl logs <pod-name>

# Check previous logs if container restarted
kubectl logs <pod-name> --previous
```

### Database Connection Issues

```powershell
# Verify PostgreSQL is running
kubectl exec -it postgres-0 -- psql -U postgres -d weatherdb -c "SELECT 1;"

# Check if table exists
kubectl exec -it postgres-0 -- psql -U postgres -d weatherdb -c "\dt"
```

### Kafka Connection Issues

```powershell
# Check Kafka logs
kubectl logs -l app=kafka

# Check Zookeeper logs
kubectl logs -l app=zookeeper
```

### Rebuild and Redeploy a Service

```powershell
# Rebuild
mvn clean package -DskipTests
docker build -t weather-station:1.0 -f Dockerfile.weather-station .

# Restart pods to pick up new image
kubectl delete pods -l app=weather-station
```

---

## Configuration

### Environment Variables

| Service | Variable | Description |
|---------|----------|-------------|
| All | `KAFKA_BOOTSTRAP_SERVERS` | Kafka broker address (default: `kafka:9092`) |
| Central Station | `DB_URL` | PostgreSQL connection URL |
| Central Station | `DB_USER` | Database username |
| Central Station | `DB_PASSWORD` | Database password (from secret) |
| Weather Station | `STATION_ID` | Pod name (auto-assigned by K8s) |

### Scaling Weather Stations

Edit `k8s/weather-station.yaml` and change `replicas`:

```yaml
spec:
  replicas: 20  # Change from 10 to desired number
```

Then apply:

```powershell
kubectl apply -f k8s/weather-station.yaml
```

---

## File Structure

```
Weather-Stations-Monitoring/
├── Dockerfile.weather-station
├── Dockerfile.central-station
├── Dockerfile.raining-detection
├── pom.xml
├── k8s/
│   ├── postgres-secret.yaml      # Database credentials
│   ├── postgres.yaml             # PostgreSQL + PersistentVolume
│   ├── zookeeper.yaml            # Zookeeper service
│   ├── kafka.yaml                # Kafka broker
│   ├── central-station.yaml      # Central Station deployment
│   ├── raining-detection.yaml    # Raining Detection deployment
│   └── weather-station.yaml      # Weather Station StatefulSet (10 pods)
└── src/
    └── main/java/weather/
        ├── WeatherStationA.java
        ├── CentralStationApp.java
        └── RainingDetectionApp.java
```
