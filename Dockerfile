# ---------- stage 1: build the jar ----------
# start from the JDK image; "AS build" gives this stage a name
FROM eclipse-temurin:17-jdk AS build
# like cd /app (created if missing)
WORKDIR /app

# copy only the build files first, then download all libraries
# This layer is reused until pom.xml changes,
# so a code-only change doesnot re-download everything
# .mvnw mvnw is maven wrapper -> small script to download Maven
# dependency:go-offline -> read pom.xml and install all of the dependency list in it
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw dependency:go-offline -q

# now copy your code and build target/Jora-0.0.1-SNAPSHOT.jar
# (-DskipTests: tests run in CI later, not in every image build)
COPY src src
RUN ./mvnw package -DskipTests -q

# ---------- stage 2: run the jar ----------
# fresh small image, nothing from stage 1 yet
FROM eclipse-temurin:17-jre
WORKDIR /app

# take ONLY the jar from stage 1
COPY --from=build /app/target/*.jar app.jar

# a note saying "this app listen on 8080 - (it does not open anything)"
EXPOSE 8080
# the command run when the container starts
ENTRYPOINT ["java", "-jar", "app.jar"]