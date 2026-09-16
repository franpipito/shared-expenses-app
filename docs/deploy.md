# Deploy del backend en Render + MongoDB Atlas

Reemplaza al deploy de Railway de la sesión 5. El motivo es plata: Railway no
tiene tier gratis permanente y el crédito de $5 se agota. Render (Docker) y
Atlas M0 son gratis de verdad, al costo de que el servicio se duerme — ver la
sección del ping.

## Qué se deploya

`backend/Dockerfile`, un build multi-stage: compila con el JDK y corre con un
JRE de Alpine. Render detecta el Dockerfile solo.

**El código no necesitó ningún cambio para mudarse de proveedor.** `server.port`
ya sale de `${PORT:8080}` (Render inyecta `PORT` igual que Railway), la conexión
ya sale de `MONGO_URI`, y el apagado graceful ya estaba. Esa es toda la ganancia
de haber configurado por variables de entorno desde el principio.

---

## 0. Qué pasó con el deploy de Railway

Vale dejarlo escrito porque explica por qué no hay migración de datos.

La sesión 5 deployó **Postgres + Flyway**, con las variables `DB_URL`,
`DB_USER`, `DB_PASSWORD` y `DDL_AUTO`. La migración a Mongo vino después y borró
Flyway, el driver de Postgres y las migraciones: hoy el `pom.xml` solo tiene
`spring-boot-starter-data-mongodb`. **`MONGO_URI` nunca se seteó en Railway.**

Así que pasó una de dos, y la conclusión es la misma:

- Si Railway tenía auto-deploy, rebuildeó con el código de Mongo y
  `ValidacionDeConfiguracion` vio el fallback a localhost y tiró
  `IllegalStateException`: **el contenedor sale con código 1**. La guarda hizo
  exactamente su trabajo.
- Si no lo tenía, sigue corriendo un jar de Postgres cuyo código ya no existe en
  el repo.

En ningún caso hay datos que valga la pena rescatar: lo que haya son los gastos
de prueba de la verificación de la sesión 5, en una base que el jar actual no
puede ni abrir. **Se arranca limpio en Atlas.**

## 1. Rotar el secreto del JWT

Este paso no se saltea, y ahora hay un motivo extra: el secreto anterior estuvo
en la consola de Railway y probablemente en alguna captura. Mudarse es el momento
natural para rotarlo. Rotarlo invalida todos los tokens emitidos, lo cual hoy no
le molesta a nadie.

En PowerShell:

```powershell
$bytes = New-Object byte[] 48
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
[Convert]::ToBase64String($bytes)
```

`RandomNumberGenerator` y no `Get-Random`: el segundo no es criptográficamente
seguro.

Ese valor va **solo** en las variables de Render. Nunca en el repo, nunca en un
mensaje, nunca en una captura. Elegí también un código de invitación nuevo,
distinto de `nutrias`.

> **Orden importante:** rotá el secreto **antes** de armar el atajo de iOS
> (`docs/atajo-ios.md`). Si lo armás antes, el token que pegues en Atajos nace
> muerto.

## 2. Crear el cluster en Atlas

1. **Build a Database → M0 (Free).**
2. **Provider y región:** AWS **`us-east-1` (N. Virginia)**, para emparejar con
   la región de Render del paso 3. Ninguno de los dos tiene región en
   Sudamérica en el tier gratis, así que hay ~110-150 ms de latencia base contra
   Argentina lo elijas donde lo elijas. Lo que sí se puede evitar es sumarle un
   salto más entre la app y la base: **Atlas y Render en la misma región.**
3. **Database Access → Add New Database User.** Password autogenerada y larga.
   **El rol va acotado**: `readWrite` sobre la base `gastos` y nada más. Nunca
   `atlasAdmin`, que es el default que ofrece la interfaz.
4. **Network Access → Add IP Address.**

### La decisión incómoda del paso 4

Atlas M0 exige lista blanca de IPs, y **el tier gratis de Render no da IP de
salida fija**. O sea que la entrada tiene que quedar en `0.0.0.0/0`.

Eso significa que **toda la seguridad de la base pasa a descansar en la
credencial**: cualquiera en internet puede intentar conectarse al cluster, y lo
único que lo frena es no tener el usuario y la password. Es un escalón para abajo
respecto de una lista blanca real, y conviene tenerlo dicho y no descubrirlo
después.

Lo que lo acota:

- Password autogenerada y larga, nunca una elegida a mano.
- El usuario con `readWrite` sobre `gastos` únicamente: si se filtra, no puede
  crear usuarios ni tocar la configuración del cluster.
- Atlas exige TLS siempre, así que la credencial no viaja en claro.

La salida real —IP fija de salida— es un plan pago de Render. Cuando el proyecto
justifique pagar, ese es el primer upgrade a hacer.

## 3. Crear el servicio en Render

**`render.yaml` está en la raíz del repo**, así que el camino corto es:

1. **New → Blueprint**, y conectar el repo `shared-expenses-app`.
2. Render lee el archivo y propone el servicio `minutria-api` ya configurado:
   Docker, el contexto en `backend/`, el health check en `/actuator/health`, la
   región y el plan free.
3. Te pide a mano los tres valores marcados `sync: false`, que son los del paso
   4: `MONGO_URI`, `JWT_SECRETO` y `CODIGO_INVITACION`.

Tener la configuración en un archivo versionado y no en un panel es la única
forma de que dentro de seis meses se pueda leer por qué está como está —- y de
poder rehacer el servicio sin acordarse de nada.

> **Ojo con la región.** En `render.yaml` está puesta en `virginia`. Si el
> cluster de Atlas quedó en otra, cambiala acá para que coincidan: base y app en
> continentes distintos son 150 ms extra en cada consulta.

Si por lo que sea hay que hacerlo a mano (**New → Web Service**), estos son los
mismos valores:

| Campo | Valor |
|---|---|
| Language / Runtime | **Docker** |
| Root Directory | **`backend`** |
| Region | **Virginia (US East)**, la misma del cluster |
| Instance Type | Free |
| Health Check Path | `/actuator/health` |

**Root Directory = `backend` no es opcional**: el `Dockerfile` está ahí, no en la
raíz. Es el mismo tropiezo que en Railway.

## 4. Variables de entorno

| Variable | Valor |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `produccion` |
| `MONGO_URI` | la cadena de Atlas, ver abajo |
| `JWT_SECRETO` | el del paso 1 |
| `CODIGO_INVITACION` | el nuevo, distinto de `nutrias` |

`ZONA_HORARIA` y `JWT_DURACION_DIAS` tienen defaults correctos y no hace falta
setearlas.

### Tres trampas de la `MONGO_URI`

La cadena sale de **Atlas → Connect → Drivers** y se ve así:

```
mongodb+srv://USUARIO:PASSWORD@cluster0.xxxxx.mongodb.net/gastos?retryWrites=true&w=majority
```

1. **El nombre de la base va en el path.** Atlas te da la cadena **sin** el
   `/gastos`, y si la pegás tal cual, Spring Data se conecta a una base llamada
   `test`. La app arranca perfecto, siembra las categorías, deja registrarse — y
   los datos van a parar a otro lado. Hay que agregar `/gastos` a mano.
2. **La password hay que URL-encodearla** si tiene `@`, `:`, `/` o `%`. Un `@` sin
   escapar parte la cadena en el lugar equivocado y el error no dice nada de
   passwords.
3. **La propiedad es `spring.mongodb.uri`, NO `spring.data.mongodb.uri`.** Esto
   ya está resuelto en `application.properties`, pero vale saberlo porque todo
   internet documenta la segunda: es la correcta en Boot 3, y en Boot 4 está
   deprecada con nivel "error", o sea que **no bindea y no avisa**. La app cae al
   default y falla con "Command createIndexes requires authentication", que no
   menciona en ningún lado cuál es el problema real.

### Por qué el perfil `produccion` no es opcional

Activa dos cosas:

- **`application-produccion.properties`**, que baja el log de MongoTemplate (que
  en debug imprime las consultas **con sus valores adentro**, o sea que un
  `findByEmail` te deja el email en el log) y garantiza que ningún error
  devuelva stacktrace.
- **`ValidacionDeConfiguracion`**, que **impide que la app arranque** con el
  secreto, el código de invitación o la URI de desarrollo. Sin el perfil, esa
  clase ni se instancia, y la app arrancaría feliz, completamente abierta y
  contra una base vacía.

## 5. El ping, que acá no es opcional

**Render duerme los servicios gratis tras ~15 minutos sin tráfico**, y el
arranque en frío de Spring Boot son 40-60 segundos. La app tiene un requisito
duro de velocidad de carga: Viole parada en el mostrador esperando un minuto es
exactamente el escenario que hace que abandone.

Un cron externo cada 10 minutos contra `/actuator/health` lo mantiene despierto.
Sirve **cron-job.org** o **UptimeRobot**, los dos con tier gratis.

**No uses un cron de GitHub Actions** para esto: los schedules se atrasan bajo
carga (pueden irse 15 minutos o más) y se autodesactivan si el repo queda quieto
un tiempo. Justo las dos cosas que no querés en lo que evita el arranque en frío.

> **Ojo con las horas de instancia.** El tier gratis de Render tiene un tope
> mensual de horas por cuenta (del orden de 750, verificá el número vigente).
> Un servicio despierto 24/7 son ~730 horas: entra, pero justo. **Un segundo
> servicio gratis despierto no entra**, así que si algún día sumás la web,
> revisá esto antes.

## 6. Verificar

Render asigna un dominio `https://NOMBRE.onrender.com`.

```powershell
curl.exe -s https://TU-DOMINIO.onrender.com/actuator/health
```

Responde así:

```json
{"groups":["liveness","readiness"],"status":"UP"}
```

`groups` es metadata de Actuator y no expone nada. Lo que importa es que **no**
aparezca un bloque `components` con el estado de la base ni su versión. Si lo
ves, `management.endpoint.health.show-details` no quedó en `never`.

La primera llamada después de un rato puede tardar 40-60 segundos: es el arranque
en frío, y es lo esperable hasta que el ping del paso 5 esté andando.

Después, registrate:

```powershell
$body = @{ nombre = "Franco"; email = "franco@minutria.app"
           password = "TU-CONTRASENA-DE-AL-MENOS-12"
           codigoInvitacion = "TU-CODIGO" } | ConvertTo-Json
Invoke-RestMethod -Uri "https://TU-DOMINIO.onrender.com/auth/registro" `
    -Method Post -ContentType "application/json" -Body $body
```

**Qué mirar en los logs de Render:** ya no hay Flyway aplicando migraciones —eso
se perdió con Postgres y es la pérdida más seria del cambio de base. Lo que sí
tiene que verse es `SembradorDeCategorias` sembrando las seis categorías, y la
creación de los índices declarados con `@Indexed` y `@CompoundIndex`.

Y en **Atlas → Browse Collections**: tienen que aparecer `categoria` con seis
documentos, más `grupo` y `usuario` con uno cada uno. Si la base que ves se llama
`test`, volvé a la trampa 1 del paso 4.

### Sobre los horarios en los logs

Los timestamps de Render van a estar en UTC y eso está bien: la zona del
contenedor no afecta a la app. Los cortes de mes usan el bean `Clock`, fijado en
`America/Argentina/Buenos_Aires`, y los tokens usan `Instant`, que no depende de
zona.

Esto ya se verificó en serio una vez: el contenedor de Railway arrancó a las
01:47 UTC del 7 de septiembre y el resumen del mes igual cortó en el 6, que era
la fecha en Buenos Aires. Sin el bean, el corte se habría adelantado seis horas
todas las noches.

## 7. Repuntar la app mobile

`mobile/src/api/cliente.ts` tiene la URL vieja de Railway clavada en `URL_API`.
Hay que cambiarla por la de Render, o la app sigue pegándole a un servicio
muerto.

Y recién después de esto armá el atajo de iOS (`docs/atajo-ios.md`), que también
apunta a una URL fija: si lo armás antes, lo hacés dos veces.

## Si algo falla

| Síntoma | Causa probable |
|---|---|
| El build no encuentra el Dockerfile | Falta **Root Directory = `backend`** |
| El build dice `BUILD SUCCESS` pero el servicio no responde | El build no es el problema: el contenedor arranca y muere. Casi siempre son las variables sin cargar — sin `JWT_SECRETO` la app sale con código 1. Los logs que sirven son los de **runtime**, no los de build |
| `IllegalStateException: Falta la variable de entorno JWT_SECRETO` | Funcionó la validación: falta cargar la variable |
| `IllegalStateException: Falta la variable de entorno MONGO_URI` | Idem, o la URI quedó apuntando a localhost |
| `Command createIndexes requires authentication` | La URI no se bindeó. Revisar que sea `spring.mongodb.uri` y no `spring.data.mongodb.uri` |
| `MongoTimeoutException` / no conecta a Atlas | Network Access sin `0.0.0.0/0`, o la password sin URL-encodear |
| La app anda pero la base está vacía en Atlas | Falta `/gastos` en el path de la URI: los datos se fueron a `test` |
| Primera request de 60 segundos | El servicio estaba dormido. Es el paso 5 |

## Cosas que se aprendieron deployando

- **`smoke-test.ps1` NO se corre contra producción.** Está clavado a
  `localhost:8080`, usa el código de invitación y la contraseña de desarrollo, y
  arranca registrando usuarios: contra la base real fallaría en la primera línea
  porque el grupo ya está completo. Es un script de la máquina de desarrollo.
- **No hay endpoint para cambiar el email ni la contraseña.** Está fuera del MVP
  a propósito: son dos personas que se registran una vez. Si hay que corregir un
  email, es un `updateOne` desde la consola de Atlas, **en minúsculas y sin
  espacios**, porque `normalizar()` busca así y una mayúscula deja a esa persona
  sin poder entrar.
- **La política de contraseñas muerde de verdad.** `violeyfran2026` se rechaza
  para `viole@minutria.app` porque empieza con la parte local del email, que es
  la primera combinación que prueba cualquier ataque dirigido.
- **La guarda de configuración se ganó el sueldo.** El deploy de Railway quedó
  caído tras la migración a Mongo justamente porque
  `ValidacionDeConfiguracion` se negó a arrancar contra una base vacía. Un
  servicio muerto y ruidoso es infinitamente mejor que uno vivo escribiendo en
  una base equivocada.

## Apagar Railway, y recién ahí

No antes de que el smoke test pase contra Render y la app funcione apuntando
ahí. Si hubiera datos que valga la pena conservar:

```powershell
mongodump --uri="LA-URI-DE-RAILWAY" --out=backup
mongorestore --uri="LA-URI-DE-ATLAS" backup
```

Para este proyecto no hace falta: como explica el paso 0, el deploy de Railway
quedó de antes de la migración a Mongo, así que **no hay nada que traer**.

## Diferencias con Railway que conviene tener presentes

| | Railway | Render free |
|---|---|---|
| Se duerme | no (mientras haya crédito) | **sí, a los 15 min** |
| Arranque en frío | no aplica | 40-60 s |
| Config versionada | en el panel | `render.yaml` en el repo |
| Base | del mismo proveedor | Atlas, cuenta aparte |
| IP de salida | fija | no, de ahí el `0.0.0.0/0` en Atlas |

La incomodidad de fondo es que **la base ya no está al lado del backend**: son
dos proveedores, dos paneles y dos cuentas que pueden vencer por separado. A
cambio, Atlas M0 es gratis para siempre y no depende de que Render siga siendo
generoso.

## Pendientes conocidos para después del deploy

- **CORS** no está configurado. La app mobile no lo necesita (no es un
  navegador), pero la versión web sí va a necesitarlo.
- **Sin backups automáticos.** Atlas M0 no los incluye. Con dos personas y datos
  recreables es asumible, pero es una decisión y no un olvido.
- **El limitador de intentos vive en memoria**: se reinicia con cada deploy y no
  funcionaría con más de una instancia.
- **La app mobile no tiene nada offline.** `cliente.ts` no tiene timeout ni
  reintento, así que un `fetch` que falla es un gasto perdido. Sumado al arranque
  en frío, es el riesgo más serio para el viaje a Bariloche.
