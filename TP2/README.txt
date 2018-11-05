cd Desktop/INF8480/INF8480/TP2/bin

Builder avec la commande 'ant' dans le repertoire TP2

Service de repertoire de noms (machine distante L4714-19)
    ssh L4714-19
    cd bin
    rmiregistry 5021 &
    cd ..
    ifconfig        // inscrire l'adresse IP retournee dans le application.properties (serviceIp)
    ./authenticationservice.sh

Serveur de calcul #1 (machine distante L4714-18)
    ssh L4714-18
    cd bin
    rmiregistry 5021 &
    cd ..
    ifconfig    // prendre en note l'IP
    ./operationserver.sh 132.207.12.178 5004 4 0 // IP PORT CAPACITY MALICIOUS_RATE (PORT DANS LE RANGE)

Serveur de calcul #2 (machine distante L4714-17)
    ssh L4714-17
    cd bin
    rmiregistry 5021 &
    cd ..
    ifconfig    // prendre en note l'IP
    ./operationserver.sh 132.207.12.177 5004 4 0 // IP PORT CAPACITY MALICIOUS_RATE (PORT DANS LE RANGE)

Lancer les autres serveurs de calcul de la meme facon sur d'autres machines
    
Repartiteur (machine locale L4714-20)
    cd bin
    rmiregistry 5021 &
    cd ..
    ./loadbalancer.sh username password operations-1


Mode securise (resultat de 2000 operations : 3336172)
    application.properties secure=true
    2 serveurs (4.9 + 4.93 + 4.98 + 4.91 + 4.95) / 5 secondes
    3 serveurs (3.3 + 3.29 + 3.26 + 3.28 + 3.29) / 5 secondes
    4 serveurs (2.47 + 2.49 + 2.48 + 2.48 + 2.43) / 5 secondes

Mode non-securise (resultat de 2000 operations : 3336172)
    application.properties secure=false
    3 serveurs de bonne foi (11.25 + 12.03 + 11.72 + 11.3 + 11.72) / 5 secondes
    2 serveurs de bonne foi, un 50% malicieux (15.06 + 16.49 + 15.79 + 15.55 + 16.12) / 5 secondes
    2 serveurs de bonne foi, un 75% malicieux (19.16 + 18.51 + 18.34 + 18.41 + 17.96) / 5 secondes

Probleme : si on lance le serveur malicieux en dernier, le temps est extremement long (double).
    

    
