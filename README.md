# pwf-filenet-microservice
Mikroslužba slouží pro manipulaci (ukládání, stahování, mazání a úpravování) s dokumenty ve FileNetu. 
Vystupuje tak jako integrační mezivrstva.

## Struktura projektu
Projekt se skládá ze dvou submodulů:

* **app** - aplikační modul obsahující business logiku pro manipulaci s dokumenty ve FileNetu

## Build nového docker image
Tento odstavec popisuje kroky potřebné k vydání docker image s novou verzí aplikace.

1. Nastavit novou verzi v elementu `<version>` v `pom.xml`, `specification/pom.xml` a `app/pom.xml`.
2. Provést příkaz `mvn install` nad submodulem `specification`.
3. S aktivním maven profilem `docker` provést příkaz `mvn install`. Tento příkaz provede build nového docker image a
   jeho následný upload do docker registry.
