# syntax=docker/dockerfile:1.7

FROM node:24.11.1-bookworm-slim@sha256:48abc13a19400ca3985071e287bd405a1d99306770eb81d61202fb6b65cf0b57 AS web-build
WORKDIR /src/web
COPY web/package.json web/package-lock.json ./
RUN npm ci --ignore-scripts
COPY web/ ./
RUN npm run build -- --outDir /src/web/build/dist

FROM eclipse-temurin:21.0.12_8-jdk-noble@sha256:75ce56643243c3db632be2ef259625fb42ee3be1334389659f7a1a61acb78783 AS java-build
WORKDIR /src
COPY . .
COPY --from=web-build /src/web/build/dist /src/web/build/dist
RUN ./gradlew :api:installDist -x :web:buildWeb --no-daemon

FROM eclipse-temurin:21.0.12_8-jre-noble@sha256:96975602e131485862eb8cd32927face8a06d7591a5e865944b634a701d9df72
RUN groupadd --system --gid 10001 samlier \
    && useradd --system --uid 10001 --gid samlier --home-dir /opt/samlier --shell /usr/sbin/nologin samlier \
    && mkdir -p /opt/samlier /data \
    && chown -R samlier:samlier /opt/samlier /data
COPY --from=java-build --chown=samlier:samlier /src/api/build/install/api/ /opt/samlier/
USER 10001:10001
WORKDIR /opt/samlier
ENV SAMLIER_DATA_DIR=/data \
    SAMLIER_HTTP_PORT=8080
EXPOSE 8080
VOLUME ["/data"]
ENTRYPOINT ["/opt/samlier/bin/api"]
