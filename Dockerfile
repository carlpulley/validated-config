FROM adoptopenjdk/openjdk11 as dev
LABEL Author="Keshav Murthy"

RUN apt-get update && apt-get install -y gnupg2 apt-transport-https

RUN \
  echo "deb https://dl.bintray.com/sbt/debian /" | tee -a /etc/apt/sources.list.d/sbt.list && \
  apt-key adv --keyserver hkps://keyserver.ubuntu.com:443 --recv 2EE0EA64E40A89B84B2DF73499E82A75642AC823 && \
  apt-get update && \
  apt-get install -y \
    sbt=1.4.9 \
    ca-certificates

RUN update-ca-certificates

WORKDIR /app

#Create docker container with the dependencies so subsequent builds are faster
ADD build.sbt ./
ADD ./project ./project
RUN sbt update

ADD . /app
