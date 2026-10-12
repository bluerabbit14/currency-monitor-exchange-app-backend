# currency-monitor-exchange-app-backend

## command to run spring framework form terminal

# Migrations run on app start (Flyway)
./mvnw spring-boot:run

.\mvnw.cmd clean

# Unit + controller tests
./mvnw test "-Dtest=AssetServiceTest,AssetControllerTest"

# Full suite including Flyway IT (needs Docker)
./mvnw test