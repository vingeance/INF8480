#!/bin/bash

pushd $(dirname $0) > /dev/null
basepath=$(pwd)
popd > /dev/null

cat << EndOfMessage
HELP: 
./authenticationservice.sh

EndOfMessage

java -cp "$basepath"/authenticationservice.jar:"$basepath"/shared.jar \
  -Djava.rmi.server.codebase=file:"$basepath"/shared.jar \
  -Djava.security.policy="$basepath"/policy \
  -Djava.rmi.server.hostname="127.0.0.1" \
  service.AuthenticationService $*
