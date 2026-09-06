# Docker

## Qué problema resuelve

Sin Docker, para tener Postgres en desarrollo hay que instalarlo en la máquina:
un instalador que toca el registro, un servicio de Windows que arranca solo, una
versión concreta que después choca con la que necesita otro proyecto, y un
"andá a saber qué configuré hace seis meses".

Docker cambia eso por **un archivo de texto versionado en el repo**. Cualquiera
que clone el proyecto corre un comando y tiene exactamente la misma base, la
misma versión, la misma configuración. Y cuando el proyecto muere, se borra sin
dejar nada instalado.

El eslogan es "funciona en mi máquina, ahora tu máquina es mi máquina".

## Los tres conceptos

Con estos tres alcanza para el 90% del uso diario.

### Imagen

Una plantilla congelada, de **solo lectura**. `postgres:17-alpine` es "un Linux
mínimo (Alpine) con Postgres 17 ya instalado y configurado". No corre: es el
molde.

Las imágenes se descargan de un registro (Docker Hub por defecto) y se cachean
localmente. Por eso el primer `docker compose up` tarda y los siguientes no.

El `:17-alpine` es el **tag**: la versión. Usar `:latest` es una mala idea en un
proyecto real, porque el día que cambie te rompe el entorno sin que hayas tocado
nada.

### Contenedor

Una **instancia en ejecución** de una imagen. Si venís de programación orientada
a objetos: la imagen es la clase, el contenedor es el objeto.

Lo importante: **los contenedores son descartables**. Borrarlos y recrearlos es
la operación normal, no una emergencia. Un contenedor no es una máquina que
cuidás, es un proceso que tirás y volvés a levantar.

### Volumen

Acá está el truco. Si los contenedores son descartables, ¿dónde viven los datos
de la base?

En un **volumen**: un espacio de disco gestionado por Docker, que vive **afuera**
del contenedor y le sobrevive. En nuestro `docker-compose.yml` se llama
`postgres_data` y está montado en `/var/lib/postgresql/data`, que es donde
Postgres guarda todo.

> **La regla mental: el contenedor es descartable, el volumen no.**

Esto explica por qué `docker compose down` es inofensivo y `docker compose down -v`
te borra la base entera. El `-v` elimina los volúmenes.

## Docker en Windows: la parte que costó

Docker es tecnología de Linux: usa características del kernel de Linux
(namespaces y cgroups) para aislar procesos. En Windows no existen, así que
Docker Desktop **corre una máquina virtual Linux** por abajo y le habla desde
Windows.

Esa VM necesita virtualización por hardware, y ahí se encadenan tres cosas:

1. **WSL2** — el subsistema de Linux de Windows, que es el que hospeda la VM.
   Se instala con `wsl --install --no-distribution` desde una terminal como
   administrador. El `--no-distribution` es porque no queremos Ubuntu ni ninguna
   distro para usar a mano: solo queremos el motor.
2. **Features de Windows** — `VirtualMachinePlatform` y Hyper-V. Los habilita el
   propio `wsl --install`, pero requieren reiniciar.
3. **Virtualización habilitada en el BIOS** — y acá fue donde se trabó.

Sobre lo tercero: el CPU soportaba virtualización, pero el firmware la tenía
apagada. El diagnóstico se ve así desde PowerShell:

```powershell
Get-CimInstance Win32_Processor | Select-Object VirtualizationFirmwareEnabled, VMMonitorModeExtensions
(Get-CimInstance Win32_ComputerSystem).HypervisorPresent
```

- `VMMonitorModeExtensions: True` → el CPU **puede**.
- `VirtualizationFirmwareEnabled: False` → el BIOS **no lo deja**.

**En AMD la opción se llama `SVM Mode`, no "Virtualization" ni "VT-x"** (eso es
nomenclatura de Intel). En una placa Gigabyte AM4 está en
`M.I.T. → Advanced Frequency Settings → Advanced CPU Core Settings → SVM Mode`.

Un detalle contraintuitivo: una vez que el hipervisor está corriendo,
`VMMonitorModeExtensions` pasa a leerse como `False`. No es un error — es que el
hipervisor se apropió de las extensiones de virtualización del CPU y ya no están
disponibles para el sistema operativo anfitrión.

## Anatomía de nuestro docker-compose.yml

`docker-compose.yml` es un archivo declarativo: describe el estado deseado, no
los pasos. Docker se encarga de llegar ahí.

```yaml
services:
  postgres:                        # nombre del servicio dentro de compose
    image: postgres:17-alpine      # qué imagen, con versión fija
    container_name: gastos-postgres # nombre fijo, para poder hacer docker exec
    environment:                   # las variables que la imagen de Postgres lee
      POSTGRES_DB: gastos          # crea esta base al inicializarse
      POSTGRES_USER: gastos        # crea este usuario
      POSTGRES_PASSWORD: gastos_local
    ports:
      - "5432:5432"                # puerto_de_windows : puerto_del_contenedor
    volumes:
      - postgres_data:/var/lib/postgresql/data   # volumen : ruta dentro del contenedor
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U gastos -d gastos"]
      interval: 5s
      timeout: 5s
      retries: 10

volumes:
  postgres_data:                   # declara el volumen gestionado por Docker
```

Tres cosas que vale entender bien:

**El mapeo de puertos `"5432:5432"`.** El de la izquierda es el puerto en tu
Windows; el de la derecha, dentro del contenedor. Por eso la app se conecta a
`localhost:5432` como si Postgres estuviera instalado en la máquina. Si algún
día tenés dos proyectos con Postgres a la vez, cambiás el de la izquierda
(`"5433:5432"`) y listo — el contenedor ni se entera.

**Las variables de entorno.** No son configuración de Docker: son variables que
**la imagen de Postgres** sabe leer. Están documentadas en la página de la imagen
en Docker Hub, y cada imagen tiene las suyas. La contraseña acá está en texto
plano y está bien, porque es la base local de desarrollo; en producción va por
variable de entorno del proveedor.

**El healthcheck.** Un contenedor "levantado" no es lo mismo que "listo": Postgres
tarda unos segundos en inicializarse y aceptar conexiones. `pg_isready` es la
herramienta oficial de Postgres para preguntar "¿ya podés atender?". Docker la
corre cada 5 segundos y marca el contenedor como `healthy` cuando responde bien.
Sin esto, la app puede arrancar antes que la base y fallar al conectar.

## Comandos

```bash
docker compose up -d          # levanta lo declarado en docker-compose.yml
docker compose ps             # qué está corriendo, y su estado de salud
docker compose logs -f postgres   # los logs, en vivo (-f = follow)
docker compose down           # apaga y borra los contenedores. LOS DATOS QUEDAN
docker compose down -v        # apaga Y BORRA LOS VOLÚMENES. Se pierde la base
```

`-d` es *detached*: corre en segundo plano y te devuelve la terminal. Sin `-d`,
la terminal queda tomada mostrando los logs.

`up -d` es **idempotente**: si ya está corriendo, no hace nada. Se puede correr
las veces que quieras.

Para meterse en la base a mano:

```bash
docker exec -it gastos-postgres psql -U gastos -d gastos
```

`exec` corre un comando dentro de un contenedor que ya está andando. `-it` es
interactivo con terminal, que es lo que necesita `psql`.

Dentro de psql: `\dt` lista las tablas, `\d gasto` muestra la estructura de una
tabla, `\q` sale.

## Lo que tengo que poder explicar

- La diferencia entre imagen, contenedor y volumen, y por qué los contenedores
  son descartables pero los volúmenes no.
- Qué hace exactamente `docker compose down -v` y por qué hay que tenerle
  respeto.
- Por qué Docker necesita virtualización en Windows y no en Linux.
- Qué significa el mapeo `"5432:5432"` y qué pasaría si lo cambio a `"5433:5432"`.
- Por qué existe un healthcheck si el contenedor "ya arrancó".
- Por qué fijar la versión de la imagen (`:17-alpine`) en vez de usar `:latest`.

## Lo que todavía no sé de Docker

Honestidad sobre los huecos, para saber qué falta:

- **Dockerfile** — cómo construir una imagen propia. Hasta ahora solo consumí
  una imagen ajena. Va a aparecer si algún día containerizo el backend.
- **Multi-stage builds** — compilar en una imagen y copiar solo el resultado a
  otra más chica. Es la forma estándar de empaquetar una app Java.
- **Redes de Docker** — cómo se hablan varios contenedores entre sí sin exponer
  puertos al host. Compose ya crea una red por mí y por eso no lo necesité.
- **Registros e imágenes propias** — publicar una imagen.
- **Docker en producción** — Railway y Render buildean solos desde el repo, así
  que probablemente no lo toque en este proyecto.
