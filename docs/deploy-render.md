# Deploy del backend en Render + MongoDB Atlas

Reemplaza a **`deploy.md`**, que documenta el deploy anterior en Railway. Ese
archivo queda como registro de lo que se aprendió ahí; el destino de ahora es
este.

## Por qué se migra

Railway se quedó sin crédito y no tiene tier gratis permanente. Render (Docker)
y Atlas M0 son gratis los dos, indefinidamente.

Lo que se paga a cambio: **el servicio gratis de Render se duerme a los 15
minutos sin tráfico**, y despertar Spring Boot tarda 40-60 segundos. Eso choca
de frente con el requisito duro del producto — la carga tiene que ser rapidísima,
parada en el mostrador. La solución es un cron externo que le pegue cada 10
minutos para que nunca se duerma (paso 5).

## 0. Antes de empezar

Necesitás un secreto de JWT nuevo y un código de invitación nuevo. **No reuses
los de Railway**: si esos valores estuvieron en un panel al que ya no vas a
entrar, no sabés quién más los vio.

En PowerShell:

```powershell
$bytes = New-Object byte[] 48
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
[Convert]::ToBase64String($bytes)
```

`RandomNumberGenerator` y no `Get-Random`: el segundo no es criptográficamente
seguro.

Ese valor va **solo** en el panel de Render. Nunca en el repo, nunca en un
mensaje, nunca en una captura.

## 1. El cluster de Atlas

1. Crear cuenta en [mongodb.com/cloud/atlas](https://www.mongodb.com/cloud/atlas)
   y elegir **M0 Free**.
2. Proveedor **AWS**, región **us-east-1 (N. Virginia)**.

> **La región no es un detalle.** Tiene que ser la misma que la de Render, que
> en `render.yaml` está puesta en `virginia`. Si la base queda en Oregon y el
> backend en Virginia, cada consulta cruza el continente ida y vuelta. La app
> hace varias consultas por pantalla y eso se nota justo donde no queremos: al
> cargar un gasto.
>
> Si Atlas no te ofrece us-east-1 en el plan gratis, elegí la que te ofrezca y
> **cambiá `region` en `render.yaml` para que coincida**. Lo que importa es que
> sean la misma, no cuál.

3. **Database Access → Add New Database User.** Usuario y contraseña propios,
   distintos de los de desarrollo. Rol: `readWrite` sobre la base `gastos`.
4. **Network Access → Add IP Address → `0.0.0.0/0`.**

> Eso es "desde cualquier IP", y conviene entender por qué acá está bien y
> normalmente no lo estaría. El plan gratis de Render no da IPs de salida
> estables, así que no hay ninguna lista que se pueda escribir. Lo que protege
> la base es entonces **una sola cosa**: la contraseña del usuario, que viaja
> dentro de `MONGO_URI` y solo existe en el panel de Render.
>
> La consecuencia práctica: esa contraseña es la única puerta. Que sea larga y
> generada, no elegida a mano. Si algún día el proyecto justifica pagar, la
> solución de verdad es un peering o IPs estáticas de salida.

## 2. La cadena de conexión

En Atlas: **Connect → Drivers**. Te da algo así:

```
mongodb+srv://usuario:clave@cluster0.xxxxx.mongodb.net/?retryWrites=true&w=majority
```

**Fijate bien: no dice contra qué base conectarse.** Hay que agregar `gastos`
entre el host y el `?`:

```
mongodb+srv://usuario:clave@cluster0.xxxxx.mongodb.net/gastos?retryWrites=true&w=majority
```

Si se pega tal cual viene, el driver usa `test` y **la app funciona igual**: el
sembrador crea las seis categorías ahí, el registro anda, todo parece bien. El
problema aparece meses después, cuando alguien busca los datos donde deberían
estar y no hay nada.

Es la clase de error que Mongo no puede detectar solo — sin esquema, una base
equivocada es indistinguible de una base nueva. Por eso
`ValidacionDeConfiguracion` ahora **se niega a arrancar** si la URI no trae el
nombre de la base. Si ves ese error al deployar, es esto.

Si la contraseña tiene caracteres raros (`@`, `/`, `:`, `?`, `#`), hay que
escaparlos en porcentaje. Lo más simple es generar una que no los tenga.

## 3. El servicio en Render

`render.yaml` ya está en la raíz del repo, así que:

1. **New → Blueprint**, conectar el repo `shared-expenses-app`.
2. Render lee el archivo y propone el servicio `minutria-api` con todo
   configurado: Docker, el contexto en `backend/`, el health check en
   `/actuator/health`, la región y el plan free.
3. Te va a pedir los tres valores marcados `sync: false`:

| Variable | Valor |
|---|---|
| `MONGO_URI` | la cadena del paso 2, **con `/gastos`** |
| `JWT_SECRETO` | el que generaste en el paso 0 |
| `CODIGO_INVITACION` | uno nuevo, distinto de `nutrias` |

`SPRING_PROFILES_ACTIVE=produccion` ya viene en el blueprint. **No es
opcional**: sin ese perfil, `ValidacionDeConfiguracion` ni se instancia y la app
arrancaría con el secreto de desarrollo que está publicado en el repo — o sea,
completamente abierta, sin una sola señal de que algo anda mal.

`PORT` la inyecta Render sola; la app la lee en `server.port=${PORT:8080}`.
`ZONA_HORARIA` no hace falta: el default ya es Buenos Aires, y es lo que define
dónde cae el corte de mes. Render corre en UTC, igual que Railway.

El primer build tarda: compila el jar adentro de Docker.

## 4. Verificar

Con la URL que te da Render (`https://minutria-api.onrender.com` o parecida):

```powershell
curl.exe -s https://TU-SERVICIO.onrender.com/actuator/health
```

Tiene que decir `{"status":"UP"}` y **no** traer `components`: eso último
significaría que está exponiendo detalle interno.

Después, el smoke test completo apuntando ahí. El script tiene la URL en una
variable arriba:

```powershell
# Cambiar $base por la URL de Render antes de correrlo
.\scripts\smoke-test.ps1
```

> **Ojo con esto:** el smoke test **escribe en la base**. Crea usuarios, carga
> gastos y los borra. Contra Atlas recién creada está bien porque está vacía,
> pero no lo corras contra una base con datos reales de Viole y Franco.
>
> Y como el registro está cerrado por código de invitación, el script necesita
> el `CODIGO_INVITACION` nuevo, no `nutrias`.

Lo que conviene mirar en particular, porque es lo que se rompe al cambiar de
infraestructura:

- **El corte de mes.** Cargar un gasto y ver que `fecha` sea la de Buenos Aires
  y no la UTC. Es la prueba real del bean `Clock`, y ya pasó una vez en Railway.
- **El total hormiga y el saldo**, que suman con `$sum` adentro de Mongo. Si los
  montos hubieran quedado como String o Double, ahí se vería.

## 5. El cron que lo mantiene despierto

Sin esto, el servicio se duerme a los 15 minutos y la próxima vez que Viole abra
la app espera casi un minuto. Es exactamente el motivo por el que abandonó el
intento anterior con Excel, así que **el ping no es un extra**.

Usar [cron-job.org](https://cron-job.org) (gratis) o el scheduler de GitHub
Actions:

- URL: `https://TU-SERVICIO.onrender.com/actuator/health`
- Cada **10 minutos** (el spin-down es a los 15; 10 deja margen).

Se le pega a `/actuator/health` y no a la raíz porque es un endpoint que existe,
responde barato y no toca la base.

> **Cuenta de horas:** el plan gratis de Render da 750 horas de instancia por
> mes. Un servicio despierto todo el mes son ~730. Entra, pero justo — no queda
> lugar para un segundo servicio gratis. Si algún día hace falta, la salida es
> pingear solo en horario de uso (digamos 8:00 a 24:00) en vez de 24/7.

## 6. Apuntar la app al backend nuevo

La URL está hardcodeada en `mobile/src/api/cliente.ts`:

```ts
export const URL_API = 'https://brave-wisdom-production-0be5.up.railway.app';
```

Cambiarla por la de Render. **Esto tiene que pasar antes del build de EAS de la
sesión 7**: un build congela ese valor adentro del bundle, así que si se buildea
apuntando a Railway y después se migra, ese build queda hablándole a un backend
muerto y hay que rehacerlo.

## 7. Recién ahí, apagar Railway

No antes de que el smoke test pase contra Render y la app funcione apuntando
ahí. Y si hay datos reales en la base de Railway, exportarlos primero:

```powershell
mongodump --uri="LA-URI-DE-RAILWAY" --out=backup
mongorestore --uri="LA-URI-DE-ATLAS" backup
```

Si todavía no hay más que datos de prueba, no vale la pena.

## Diferencias con Railway que conviene tener presentes

| | Railway | Render free |
|---|---|---|
| Se duerme | no (mientras haya crédito) | **sí, a los 15 min** |
| Arranque en frío | no aplica | 40-60 s |
| Config versionada | en el panel | `render.yaml` en el repo |
| Base | Postgres/Mongo del mismo proveedor | Atlas, cuenta aparte |
| IP de salida | fija | no, de ahí el `0.0.0.0/0` en Atlas |

La incomodidad de fondo es que **la base ya no está al lado del backend**: son
dos proveedores, dos paneles y dos cuentas que pueden vencer por separado. A
cambio, Atlas M0 es gratis para siempre y no depende de que Render siga siendo
generoso.
