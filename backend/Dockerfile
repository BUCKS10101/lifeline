FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q dependency:go-offline
COPY src src
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --no-create-home appuser
COPY --from=build /workspace/target/*.jar app.jar
USER appuser
EXPOSE 8080
# Startup flags for small hosts (a free-tier instance gets a fraction of a CPU). Measured at 0.25 CPU / 512 MB:
# startup dropped from about 133-160 s to about 56 s. C1-only JIT skips the expensive optimising compiler, which
# a short-lived startup never repays; SerialGC avoids parallel GC threads that have no cores to run on.
ENTRYPOINT ["java", "-XX:TieredStopAtLevel=1", "-XX:+UseSerialGC", "-XX:CICompilerCount=1", "-jar", "/app/app.jar"]
