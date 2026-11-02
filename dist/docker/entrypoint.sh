#!/bin/sh
# metatron image entrypoint.
#   docker run <image> --help   → print usage and exit
#   docker run <image> <args>   → run the VM (default CMD boots docker.boot.mtron)
if [ "${1-}" = "--help" ] || [ "${1-}" = "-h" ] || [ "${1-}" = "help" ]; then
    cat /app/usage.txt
    exit 0
fi
exec java \
    --enable-native-access=ALL-UNNAMED \
    --add-modules jdk.incubator.vector \
    --add-opens java.base/java.lang=ALL-UNNAMED \
    --add-opens java.base/java.lang.invoke=ALL-UNNAMED \
    --add-opens java.base/java.lang.reflect=ALL-UNNAMED \
    --add-opens java.base/java.util=ALL-UNNAMED \
    --add-opens java.base/java.util.concurrent.atomic=ALL-UNNAMED \
    --add-opens java.base/java.io=ALL-UNNAMED \
    --add-opens java.base/java.nio=ALL-UNNAMED \
    --add-opens java.base/java.net=ALL-UNNAMED \
    --add-opens java.base/sun.nio.cs=ALL-UNNAMED \
    --sun-misc-unsafe-memory-access=allow \
    -jar /app/metatron.jar "$@"
