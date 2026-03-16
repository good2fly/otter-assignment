# syntax=docker/dockerfile:1
FROM gradle:jdk25-alpine@sha256:c60c0cfda2348ff28c0d561428a5aab20c23211e165ab3350a95b33cb15ab495 AS builder
WORKDIR /app

COPY settings.gradle build.gradle ./
RUN /usr/bin/gradle --no-daemon installDist

COPY src ./src
RUN /usr/bin/gradle --no-daemon installDist

FROM amazoncorretto:25-alpine
WORKDIR /app
COPY --from=builder /app/build/install/challenge /app/build/install/challenge
ENTRYPOINT ["/app/build/install/challenge/bin/challenge"]
