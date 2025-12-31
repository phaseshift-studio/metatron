FROM eclipse-temurin:22

COPY / /metatron/
WORKDIR /metatron
RUN ls
RUN echo "checking metatron directory"
RUN chmod -R 775 .
RUN chmod a+x bin/metatron
ENTRYPOINT [ "bin/metatron"  ]