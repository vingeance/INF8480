#!/bin/bash

pushd $(dirname $0) > /dev/null
basepath=$(pwd)
popd > /dev/null

cat << EndOfMessage
HELP: 
./loadbalancer.sh username password operations_filename
	- username: (REQUIRED) Nom d'utilisateur
	- password: (REQUIRED) Mot de passe
	- operations_filename: (REQUIRED) Nom du fichier d'operations.

EndOfMessage

java -cp "$basepath"/loadbalancer.jar:"$basepath"/shared.jar -Djava.security.policy="$basepath"/policy loadbalancer.LoadBalancer $*
