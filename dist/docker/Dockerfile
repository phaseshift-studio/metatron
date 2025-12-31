#   metatron w/
#   ░█▀▄░█▀█░█▀▀░█░█░█▀▀░█▀▄
#   ░█░█░█░█░█░░░█▀▄░█▀▀░█▀▄
#   ░▀▀░░▀▀▀░▀▀▀░▀░▀░▀▀▀░▀░▀
#           PhaseShift Studio
ARG JRE_BUILD_IMAGE="eclipse-temurin:22-jdk-alpine"
ARG JRE_RUNTIME_IMAGE="alpine:latest"
ARG METATRON_VERSION="0.1-SNAPSHOT"

# construct dependency graph for a custom jre
FROM ${JRE_BUILD_IMAGE} AS jre-build
ARG METATRON="0.1-SNAPSHOT"
WORKDIR /app
COPY target/metatron-${METATRON}-jar-with-dependencies.jar metatron.jar
RUN jar -xf metatron.jar
RUN jdeps --ignore-missing-deps -q \
    --recursive \
    --multi-release 21 \
    --print-module-deps \
    --class-path 'BOOT-INF/lib/*' \
    metatron.jar > deps.info
RUN $JAVA_HOME/bin/jlink \
    --add-modules $(cat deps.info) \
    --strip-debug \
    --no-man-pages \
    --no-header-files \
    --output /javaruntime

####################################################################################################################
####################################################################################################################
####################################################################################################################

# create custom jre on a small linux distribution 
FROM ${JRE_RUNTIME_IMAGE} AS runtime
ARG METATRON="0.1-SNAPSHOT"
MAINTAINER phaseshift.studio
RUN apk add --no-cache fontconfig
SHELL ["/bin/sh", "-c"]
ENV JAVA_HOME=/opt/jre
ENV PATH="$JAVA_HOME/bin:$PATH"
COPY --from=jre-build /javaruntime $JAVA_HOME
COPY target/metatron-${METATRON}-jar-with-dependencies.jar target/metatron-${METATRON}-jar-with-dependencies.jar
RUN chmod +x target/metatron-${METATRON}-jar-with-dependencies.jar
COPY examples/ examples/
COPY conf/ conf/
RUN touch .metatron.history
RUN chmod 775 .metatron.history
COPY bin/metatron bin/metatron
COPY bin/entrypoint.sh bin/entrypoint.sh
RUN chmod +x bin/metatron
RUN chmod +x bin/entrypoint.sh
EXPOSE 8999
EXPOSE 8777
# ARG option="[host=><ws://127.0.0.1:8999>,cluster=>{,},boot=><examples/docker.mtron>,log=>info]"
ENTRYPOINT ["bin/metatron", "$@"]