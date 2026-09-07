# Build multi-stage.
#
# La idea: compilar en una imagen que tiene todo lo necesario para construir
# (JDK, Maven, el codigo fuente, las dependencias) y copiar SOLO el jar
# resultante a una imagen minima que apenas puede ejecutar Java.
#
# Sin esto, la imagen que corre en produccion cargaria con el JDK entero, Maven,
# el codigo fuente y el repositorio de dependencias: cientos de megas de cosas
# que no se usan en runtime y que, cada una, es superficie de ataque.
#
#   etapa build  (eclipse-temurin:21-jdk)       ~213 MB, se descarta
#   etapa final  (eclipse-temurin:21-jre-alpine) ~71 MB + el jar
#
# Solo la ultima etapa termina en la imagen final.

# ---------------------------------------------------------------- etapa build
FROM eclipse-temurin:21-jdk AS build

WORKDIR /build

# Se copian primero SOLO los archivos que declaran dependencias, y se bajan.
# Docker cachea cada instruccion como una capa: mientras el pom.xml no cambie,
# esta capa se reutiliza y el build no vuelve a descargar internet entero.
# Si copiaramos todo junto, cualquier cambio en un .java invalidaria la cache y
# cada build se bajaria las dependencias de nuevo.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

# Ahora si el codigo, que es lo que cambia seguido.
COPY src/ src/
# Los tests ya corrieron localmente y en CI; repetirlos aca solo alarga el
# deploy. Ademas BackendApplicationTests necesita una base, que en el build no
# existe.
RUN ./mvnw -B -DskipTests package

# ---------------------------------------------------------------- etapa final
FROM eclipse-temurin:21-jre-alpine

# Correr como root dentro del contenedor no hace falta para nada, y si alguien
# encuentra una vulnerabilidad en la app, arranca con menos privilegios.
RUN addgroup -S app && adduser -S app -G app
USER app

WORKDIR /app

# Lo unico que cruza de una etapa a la otra.
COPY --from=build /build/target/*.jar app.jar

EXPOSE 8080

# exec form (con corchetes) y no shell form: asi Java es el proceso 1 del
# contenedor y recibe directo la senial de apagado. Con la forma de shell, el
# proceso 1 seria /bin/sh, que no reenvia la senial, y el apagado ordenado que
# configuramos con server.shutdown=graceful nunca ocurriria.
ENTRYPOINT ["java", "-jar", "app.jar"]
