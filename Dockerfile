ARG JAVA_IMAGE="openjdk:26-ea-26-jdk"
ARG METATRON_VERSION 

FROM ${JAVA_IMAGE}
ARG METATRON='0.1-SNAPSHOT'
MAINTAINER phaseshift.studio
SHELL ["/bin/bash", "-c"]
RUN echo "building docker image for metatron-${METATRON_VERSION}"
# RUN mvn clean install
COPY target/metatron-0.1-SNAPSHOT-jar-with-dependencies.jar target/metatron-0.1-SNAPSHOT-jar-with-dependencies.jar
COPY examples/ examples/
COPY conf/ conf/
COPY bin/metatron bin/metatron
COPY bin/entrypoint.sh bin/entrypoint.sh
RUN chmod +x bin/metatron
RUN chmod +x bin/entrypoint.sh
EXPOSE 8999
# ARG option="[host=><ws://127.0.0.1:8999>,cluster=>{,},boot=><examples/docker.mtron>,log=>info]"
ENTRYPOINT ["bin/entrypoint.sh"]