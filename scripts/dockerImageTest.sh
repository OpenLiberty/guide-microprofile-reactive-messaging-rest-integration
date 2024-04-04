#!/bin/bash
while getopts t:d: flag;
do
    case "${flag}" in
        t) DATE="${OPTARG}";;
        d) DRIVER="${OPTARG}";;
        *) echo "Invalid option";;
    esac
done

echo "Testing latest OpenLiberty Docker image"

sed -i "\#</containerRunOpts>#a<install><runtimeUrl>https://public.dhe.ibm.com/ibmdl/export/pub/software/openliberty/runtime/nightly/""$DATE""/""$DRIVER""</runtimeUrl></install>" inventory/pom.xml system/pom.xml
cat inventory/pom.xml system/pom.xml

sed -i "s;FROM icr.io/appcafe/open-liberty:kernel-slim-java11-openj9-ubi;FROM cp.stg.icr.io/cp/olc/open-liberty-daily:full-java11-openj9-ubi;g" system/Dockerfile inventory/Dockerfile
sed -i "s;RUN features.sh;#RUN features.sh;g" inventory/Dockerfile system/Dockerfile
cat inventory/Dockerfile system/Dockerfile

echo "$DOCKER_PASSWORD" | docker login -u "$DOCKER_USERNAME" --password-stdin cp.stg.icr.io
docker pull "cp.stg.icr.io/cp/olc/open-liberty-daily:full-java11-openj9-ubi"
echo "build level:"
docker inspect --format "{{ index .Config.Labels \"org.opencontainers.image.revision\"}}" cp.stg.icr.io/cp/olc/open-liberty-daily:full-java11-openj9-ubi

../scripts/testApp.sh
