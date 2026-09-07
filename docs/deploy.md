# Deploy del backend en Railway

## Qué se deploya

El backend se construye con `backend/Dockerfile` (build multi-stage: compila con
el JDK, corre con un JRE de Alpine). Railway detecta el Dockerfile solo; no hay
que configurar buildpacks ni comandos de build.

## 1. Generar el secreto del JWT

**Antes que nada**, y este paso no se saltea. El secreto que está en
`application.properties` es de desarrollo y está publicado en el repo: quien lo
lea puede firmar tokens válidos para cualquier usuario.

En PowerShell:

```powershell
$bytes = New-Object byte[] 48
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
[Convert]::ToBase64String($bytes)
```

`RandomNumberGenerator` y no `Get-Random`: el segundo no es criptográficamente
seguro y su salida es predecible si alguien conoce la semilla.

Ese valor va **solo** en las variables de Railway. Nunca en el repo, nunca en un
mensaje, nunca en una captura.

Elegí también un código de invitación nuevo, distinto de `nutrias`.

## 2. Crear el proyecto

1. En Railway, **New Project → Deploy from GitHub repo** y elegí
   `shared-expenses-app`.

> **OJO, acá se traba todo el mundo.** Un proyecto de Railway es un contenedor
> de servicios: el backend va a ser uno y Postgres otro. La configuración de
> build vive en **cada servicio**, NO en Project Settings.
>
> Si abriste Project Settings y ves *Usage, Environments, Members, Tokens…*,
> estás en el lugar equivocado y Root Directory no va a aparecer nunca.
>
> Para llegar al lugar correcto: **primer ícono de la barra lateral izquierda**
> (el de nodos conectados) → click en la tarjeta del servicio → pestaña
> **Settings** de ese panel.

2. En **Settings del servicio → sección Source → Root Directory**: poné
   `backend`. Sin esto Railway busca el Dockerfile en la raíz del repo, no lo
   encuentra, e intenta adivinar cómo construir el proyecto.
3. En **Settings del servicio → sección Deploy → Health Check Path**:
   `/actuator/health`. Railway consulta ahí antes de mandar tráfico al
   contenedor nuevo. Sin health check, un deploy roto recibe requests igual.

## 3. Agregar Postgres

**New → Database → Add PostgreSQL**, dentro del mismo proyecto.

Railway expone las credenciales como variables del servicio de Postgres
(`PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`).

> **Ojo:** Railway también expone una `DATABASE_URL` con formato
> `postgresql://usuario:pass@host:puerto/db`. **Esa NO sirve tal cual**: Java
> necesita una URL JDBC, que empieza con `jdbc:postgresql://` y no lleva las
> credenciales adentro. Por eso abajo se arma a mano.

## 4. Variables de entorno del backend

En el servicio del backend, **Variables**:

| Variable | Valor |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `produccion` |
| `DB_URL` | `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}` |
| `DB_USER` | `${{Postgres.PGUSER}}` |
| `DB_PASSWORD` | `${{Postgres.PGPASSWORD}}` |
| `JWT_SECRETO` | el que generaste en el paso 1 |
| `CODIGO_INVITACION` | uno nuevo, distinto de `nutrias` |

La sintaxis `${{Postgres.PGHOST}}` es la referencia entre servicios de Railway:
si el servicio de Postgres se llama distinto, cambiá `Postgres` por su nombre.

**`PORT` la inyecta Railway sola**, no hay que definirla. La app la lee en
`server.port=${PORT:8080}`.

`ZONA_HORARIA` no hace falta: el default ya es `America/Argentina/Buenos_Aires`.
Es importante que quede así — Railway corre en UTC y sin eso un gasto de las
21:00 del 30 de septiembre en Buenos Aires contaría como octubre.

### Por qué el perfil `produccion` no es opcional

Activa dos cosas:

- **`application-produccion.properties`**, que baja el log de SQL, apaga el
  formateo y garantiza que ningún error devuelva stacktrace.
- **`ValidacionDeConfiguracion`**, que **impide que la app arranque** si detecta
  el secreto o el código de invitación de desarrollo. Sin el perfil, esa clase
  ni se instancia, y la app arrancaría feliz y completamente abierta.

## 5. Verificar

Railway asigna un dominio en **Settings → Networking → Generate Domain**.

```powershell
curl.exe -s https://TU-DOMINIO.up.railway.app/actuator/health
```

Responde así:

```json
{"groups":["liveness","readiness"],"status":"UP"}
```

`groups` es metadata de Actuator y no expone nada: solo dice que existen esos dos
grupos de sondas. Lo que importa es que **no** aparezca un bloque `components`
con el estado de la base, su versión o el pool de conexiones. Si lo ves,
`management.endpoint.health.show-details` no quedó en `never`.

Después, registrate:

```powershell
$body = @{ nombre = "Franco"; email = "franco@minutria.app"
           password = "TU-CONTRASENA-DE-AL-MENOS-12"
           codigoInvitacion = "TU-CODIGO" } | ConvertTo-Json
Invoke-RestMethod -Uri "https://TU-DOMINIO.up.railway.app/auth/registro" `
    -Method Post -ContentType "application/json" -Body $body
```

En los logs de Railway tenés que ver a Flyway aplicando las tres migraciones
sobre la base nueva:

```
Migrating schema "public" to version "1 - esquema inicial"
Migrating schema "public" to version "2 - categorias iniciales"
Migrating schema "public" to version "3 - token version"
```

**Ese es el momento en que se paga haber elegido borrar el volumen local en vez
de hacer baseline:** estas migraciones ya corrieron de cero en la máquina de
desarrollo, así que no es la primera vez que se ejecutan.

### Sobre los horarios en los logs

Los timestamps de Railway van a estar en UTC (`01:15:49.754Z`) y eso está bien:
la zona horaria del contenedor no afecta a la app. Los cortes de mes usan el bean
`Clock`, que está fijado en `America/Argentina/Buenos_Aires`, y los tokens usan
`Instant`, que no depende de zona. Logs en UTC es además la práctica habitual
cuando el servidor puede estar en cualquier lado.

## 6. Pausar el servicio entre sesiones

Railway no tiene plan gratuito permanente: el credito inicial se consume mientras
el contenedor corre, aunque nadie lo use. Hasta que la app este en el celular de
Viole (sesion 7) no hace falta que este prendido las 24 horas.

**Settings del servicio → Pause / Remove.** Se despierta con un redeploy y las
variables quedan guardadas. Postgres se puede dejar online: su costo es mucho
menor y ahi vive el estado.

## Si algo falla

| Síntoma | Causa probable |
|---|---|
| El build no encuentra el Dockerfile | Falta **Root Directory = `backend`** |
| El build dice `BUILD SUCCESS` pero el health check falla once veces con `service unavailable` | El build NO es el problema: el contenedor arranca y muere. `service unavailable` a secas significa que nada escucha en el puerto. Casi siempre son las variables sin cargar: sin `JWT_SECRETO` la app sale con codigo 1. **Los logs que sirven son los de Deploy Logs, no los de Build Logs**: son dos pestañas distintas y en las de build no aparece una sola linea de la app |
| `IllegalStateException: Falta la variable de entorno JWT_SECRETO` | Funcionó la validación: falta cargar la variable |
| `Schema validation: missing table` | Flyway no corrió. Revisar que `spring-boot-starter-flyway` esté en el pom, no solo `flyway-core` |
| `Connection refused` a la base | La URL quedó en formato `postgresql://` en vez de `jdbc:postgresql://` |
| `UnknownHostException` con `.railway.internal` | La red privada de Railway resuelve por IPv6 y a veces tarda en estar lista. Probar con las variables públicas del Postgres |
| El deploy dice *successful* y *Online* pero la URL no existe | El servicio arranca **Unexposed**. Falta **Settings → Networking → Generate Domain** |

## Cosas que se aprendieron deployando

- **`smoke-test.ps1` NO se corre contra produccion.** Esta clavado a
  `localhost:8080`, usa el codigo de invitacion y la contrasena de desarrollo, y
  arranca registrando usuarios: contra la base real fallaria en la primera linea
  porque el grupo ya esta completo. Es un script de la maquina de desarrollo.
- **No hay endpoint para cambiar el email ni la contrasena.** Esta fuera del MVP
  a proposito: son dos personas que se registran una vez. Si hay que corregir un
  email, es un `UPDATE usuario SET email = '...' WHERE id = ...` en la consola de
  Railway, **en minusculas y sin espacios**, porque `normalizar()` busca asi y
  una mayuscula deja a esa persona sin poder entrar.
- **La politica de contrasenas muerde de verdad.** `violeyfran2026` se rechaza
  para `viole@minutria.app` porque empieza con la parte local del email, que es
  la primera combinacion que prueba cualquier ataque dirigido.
- **El `Clock` con zona horaria se justifico el primer dia.** El contenedor
  arranco a las 01:47 UTC del 7 de septiembre y el resumen del mes igual corto
  en el 6, que era la fecha en Buenos Aires. Sin el bean, el corte de mes se
  habria adelantado seis horas todas las noches.

## Pendientes conocidos para después del deploy

- **CORS** no está configurado. La app mobile no lo necesita (no es un
  navegador), pero la versión web sí va a necesitarlo.
- **Sin backups automáticos** de la base más allá de lo que dé el plan.
- **El limitador de intentos vive en memoria**: se reinicia con cada deploy y no
  funcionaría con más de una instancia.
