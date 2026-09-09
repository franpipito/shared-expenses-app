# Gastos compartidos — guia para Claude Code

## Como trabajar en este repo

Este proyecto es, ademas de una app real, el vehiculo de aprendizaje de Java +
Spring Boot de su autor (viene de Node/Express/MySQL y React Native). Eso cambia
las reglas del juego:

1. **Ir por sesiones.** Cerrar cada sesion con algo corriendo y probado antes de
   avanzar a la siguiente. No adelantar trabajo de sesiones futuras.
2. **Explicar antes de generar.** Cuando aparezca un concepto de Java o Spring
   por primera vez, explicarlo brevemente. No dar por sentado que se conoce.
3. **Discutir los trade-offs antes de implementar.** Si una decision de diseno
   tiene alternativas reales, plantearlas con su costo y recomendar una, en vez
   de elegir en silencio.
4. **Prioridad: que el autor pueda explicar el codigo en una entrevista tecnica**,
   por encima de terminar rapido.

Lo que el autor ya sabe y no hace falta explicar: arquitectura por capas, APIs
REST, modelado relacional, SQL, idempotencia, outbox, colas, rate limiting,
locks condicionales en base (`UPDATE ... WHERE`), React Native + TypeScript, git.

### Dos cosas que las maneja el autor, no Claude

**Git.** El autor commitea y pushea siempre el. **No correr `git commit` ni
`git push`.** En su lugar, cerrar la tarea entregandole el texto listo para
copiar y pegar: un bloque con el `git add` y otro con el `git commit`.
**Sin trailer de `Co-Authored-By` ni ninguna mencion a Claude en el mensaje.**

**La base de datos.** **No ejecutar nada contra la base sin pedirle permiso
antes.** Aplica a migraciones y tambien a `INSERT`, `UPDATE`, `DELETE` y
`TRUNCATE`, incluso en la base local de desarrollo. Consultas de solo lectura
para diagnosticar estan bien; ante la duda, preguntar. Proponer el SQL y esperar
el si.

## Que es la app

**Se llama MiNutria.** Los dos usuarios son **Viole** (la usuaria de la
entrevista) y **Franco** (el autor). Usar esos nombres en todo lo que sea copy,
mockups o datos de ejemplo, nunca placeholders genericos.

Registro de gastos **personal primero, de pareja despues**. Cada gasto se marca
como **hormiga** (evitable) o no, y el numero principal de la app es cuanto suma
lo evitable en el mes. Ademas, un gasto puede ser personal o compartido, y en
ese caso el sistema calcula quien le debe a quien.

> Este encuadre salio de la entrevista con la usuaria real, no de una suposicion.
> Ver **`docs/entrevista-usuaria.md`**, que es la fuente de verdad de las
> decisiones de producto. Leerlo antes de proponer features.

MVP (nada mas que esto): auth de dos usuarios de un mismo grupo, cargar gasto,
listar gastos del mes con filtros, totales del mes (incluido el total hormiga),
y saldo actual.

Fuera de alcance por ahora: metas, gastos recurrentes, graficos, export,
multi-moneda, adjuntos, push, tercer usuario en el grupo, etiquetas genericas,
importar movimientos de Mercado Pago.

**Presupuestos quedan descartados**, y no solo por alcance: la usuaria tiene
ingresos irregulares (es freelance) y gastos que no puede evitar, asi que un
tope fijo mensual no le sirve. No proponerlos.

### El requisito duro: la carga tiene que ser rapidisima

La usuaria abandono un intento anterior (Excel) porque anotar era incomodo en el
celular, y dijo explicitamente que prefiere olvidarse un gasto antes que anotar
lento. Carga parada en el mostrador, esperando el pedido.

**Tres campos y nada mas: categoria, monto, descripcion** (mas el toggle de
hormiga). Cada campo extra que se le agregue al formulario se paga en abandono.

### Las nutrias: el diferencial de producto

Toda la identidad visual de la app son **nutrias**, porque a la usuaria le
encantan. No es decoracion: es la razon emocional por la que va a abrir la app
un martes cualquiera, y por lo tanto es el antidoto directo al riesgo numero uno
del proyecto, que es el abandono.

**La nutria tiene estado de animo**, y refleja como viene la economia del mes:
contenta cuando las cosas van bien, y molesta o preocupada cuando se acumulan
los gastos hormiga.

**Restriccion dura sobre la regla de animo:** NO puede depender de un umbral
absoluto de plata. La usuaria es freelance con ingresos irregulares y ya dijo
que un tope mensual fijo no le sirve (respuesta 14 de la entrevista). El animo
tiene que salir de una medida **relativa**: la proporcion de gasto hormiga sobre
el total del mes, o la comparacion contra el mes anterior. Las dos son
independientes de cuanto entro ese mes.

La regla vive en el **backend**, por el mismo motivo que la regla del centavo:
mobile y web tienen que mostrar la misma nutria, y hay que poder ajustar los
umbrales sin redeployar las apps.

**Definido en la sesion 3:** son **tres** estados (`CONTENTA` / `TRANQUILA` /
`PREOCUPADA`) y la regla es de tendencia contra el mismo tramo del mes anterior.
Ver "El animo de la nutria" mas abajo. Tres estados = tres ilustraciones para el
mockup.

**Pendiente de conversar:** que tan dura es la nutria enojada. Es una app de
finanzas para alguien que ya se siente mal cuando se queda sin plata antes de
cobrar; una app que la haga sentir culpable se desinstala. "Preocupada" y
"orgullosa cuando mejoras" suele sostener mas el uso que "enojada". Decision de
Franco, que conoce a la usuaria.

### Direccion visual: `docs/diseno.md`
Los colores (en oklch), las tipografias, las cuatro nutrias y **la estructura de
carpetas de cada stack** viven ahi. Dos reglas que conviene tener presentes sin
abrir el archivo:

- **El ambar es del gasto hormiga y de nada mas.** Si decora botones o titulos,
  deja de significar algo, y significar algo es todo su trabajo.
- **En React y React Native no se organiza por capas sino por features.** MVC
  agrupa por tipo de archivo, lo que en React obliga a abrir tres carpetas para
  tocar una pantalla. El backend si es MVC por capas y no cambia.

Se generaron dos mockups (v0/Vercel y Lovable) y **los evaluo Viole**. Su
veredicto, que es el que manda:

- **Las nutrias de Lovable.** Ilustraciones con personalidad y consistentes: una
  flotando de espaldas en el agua, una parada, una preocupada, y **dos juntas**
  para la seccion de pareja. Las de Vercel salieron genericas y ni parecian
  nutrias.
- **La tipografia y la estructura de Vercel.** Serif de alto contraste para
  titulos y numeros, etiqueta en versalitas arriba de cada pantalla, saludo
  personal en el header, boton de accion ancho abajo. Se ve disenado y no
  templateado.
- **Dos nutrias que sean ellos**, no una sola.

Pareja tipografica elegida: **Fraunces** (display, numeros y titulos) +
**Nunito Sans** (cuerpo). Paleta: crema de fondo, teal tranquilo, ambar para el
numero protagonista, terracota para la accion principal.

**Lo que Lovable acerto y hay que conservar si o si:** el numero grande es el
total hormiga (no el total del mes), la comparacion dice "los primeros 6 dias de
agosto" y no "el mes pasado", y **la marca de hormiga se ve en cada gasto de la
lista** -- los dos ubers de $4.000, uno marcado y el otro no. Vercel perdio esa
distincion, que es literalmente el producto.

**Lo que hay que corregir en cualquier iteracion:** las categorias son las
nuestras (cafe, uber, comida, ropa, regalos, otros), los nombres son Viole y
Franco, y las pastillas de contenta/tranquila/preocupada son un control del
mockup, no un elemento de la app: el animo lo decide el backend.

## Stack

| Pieza    | Tecnologia                                    | Estado |
|----------|-----------------------------------------------|--------|
| Backend  | Java 21 + Spring Boot 4.1.1 + MongoDB 8       | en curso |
| DB local | Docker Compose (`docker compose up -d`)       | en curso |
| Mobile   | Expo + React Native + TypeScript              | pendiente |
| Web      | React + Vite + TypeScript + Tailwind          | pendiente |
| Deploy   | Railway (se acaba el credito) -> Render + Atlas | a migrar |

Build con el **Maven wrapper** (`./mvnw`, `mvnw.cmd`): no hace falta instalar
Maven, el script baja la version que el proyecto declara.

> Nota: el plan original decia Spring Boot 3, pero 3.x ya salio de soporte y
> Initializr solo ofrece 4.x. Vamos con 4.1.1. Impacto practico: mucha
> documentacion y muchos tutoriales de internet siguen siendo de Boot 3.
> Diferencias visibles: el starter web ahora es `spring-boot-starter-webmvc`
> (antes `spring-boot-starter-web`) y los starters de test estan separados por
> modulo (antes uno solo, `spring-boot-starter-test`). Las anotaciones del dia
> a dia (`@Entity`, `@RestController`, `@Service`, `@Repository`, Spring Data
> JPA) no cambiaron.

## Decisiones de diseno tomadas

### Montos: `BigDecimal` / `NUMERIC(12,2)`
Nunca `double`: el punto flotante binario no representa `0.10` exacto. El costo
de `BigDecimal` es que la aritmetica es por metodos (`.add()`, `.multiply()`) y
toda division obliga a elegir `RoundingMode` explicito. Ojo con `equals()`, que
compara escala (`2.0` != `2.00`); para comparar valor va `compareTo()`.

### Reparto: resuelto al escribir, no al leer
`Gasto` guarda `monto` y `montoPagador` (cuanto de ese gasto le toca a quien
pago). La deuda que genera el gasto es `monto - montoPagador`, sin ninguna
division en lectura. El porcentaje 50/50 es un input de UI que se traduce a
`montoPagador` al crear el gasto.

Motivo: un gasto de $10.01 al 50/50 son $5.005 por persona y alguien se come el
centavo. Guardando el reparto resuelto, esa decision se toma una sola vez, al
escribir, y queda congelada en la fila. Con un porcentaje se recalcularia en
cada lectura del saldo.

Camino de migracion si algun dia entra un tercer integrante: mover
`montoPagador` a una tabla `participacion(gasto_id, usuario_id, monto)`.

### `tipo` (PERSONAL / COMPARTIDO) se guarda explicito
No es derivable de los montos: un gasto COMPARTIDO donde el pagador cubre el
100% tiene los mismos numeros que uno PERSONAL, pero significa otra cosa.

### Gasto hormiga: `boolean esHormiga` en `Gasto`
La feature central de la app. Un gasto hormiga es el que, mirado en frio, podria
no haberse hecho.

**No es un monto chico ni una categoria.** El mismo Uber por el mismo monto es
necesario si fue por seguridad y hormiga si fue por comodidad. Es un juicio que
solo puede emitir quien carga el gasto, en el momento de cargarlo, asi que no
puede vivir en la categoria ni deducirse del monto.

Se eligio un booleano y no un sistema de etiquetas genericas porque la usuaria
pidio **un** total, y un selector de etiquetas es mas lento que un toggle -- lo
que choca de frente con el requisito de velocidad de carga. Tampoco un enum de
tres estados, porque obligaria a decidir en cada carga.

Camino de migracion si algun dia quiere mas etiquetas: el booleano pasa a ser una
fila de una tabla `etiqueta`, igual que `montoPagador` pasaria a `participacion`.

### Dos secciones, y `tipo` gobierna la visibilidad
La usuaria pidio "una seccion personal y una de pareja". Eso mapea directo sobre
`TipoGasto`, que asi deja de ser una prolijidad y pasa a ser el eje del producto:
**el mismo campo define en que seccion aparece el gasto y quien puede verlo.**

Regla: **un gasto PERSONAL lo ve solo su dueno; uno COMPARTIDO lo ven los dos.**

El motivo es concreto: quiere que los regalos que compra sigan siendo sorpresa.
No es un pedido de privacidad general -- dijo que compartir no le incomoda.

Quien pregunta se resuelve con la interfaz `UsuarioActual`. En la sesion 2 la
implementaba un lector de headers; en la 4 pasa a leer el JWT. **Cambiar de una a
otra no toco ni un servicio ni un controlador**, que era el punto de la costura.

No tienen economia compartida (ingresos separados). El encuadre de la seccion de
pareja es **"quien le debe a quien"**, no "nuestra plata".

### Autenticacion: Spring Security + filtro JWT propio
Spring Security arma la cadena de filtros y aporta BCrypt; el parseo y la
validacion del token los hace `FiltroJwt`, escrito a mano, para poder seguir el
recorrido de una request autenticada linea por linea.

**El token lleva solo el id del usuario y la expiracion.** Es un identificador,
no un portador de permisos: el nombre y el grupo se leen de la base en cada
request, asi que no hay copias que queden viejas.

Cosas que conviene tener presentes:

- **Un JWT no esconde nada.** Las tres partes son base64, no encriptacion.
  Cualquiera puede leer el payload; lo que garantiza la firma es que nadie lo
  modifico.
- **Un token emitido no se puede revocar.** No hay estado en el servidor. Con
  30 dias de vida, un token filtrado sirve 30 dias. Es el precio de haber evitado
  el refresh token, y es defendible para dos personas, pero es una decision de
  seguridad y no un detalle de configuracion.
- **`FiltroJwt` no carga el `Usuario`, solo su id.** Si lo cargara, la entidad
  quedaria detached (el filtro corre fuera de toda transaccion) y el primer
  `getGrupo()` explotaria con `LazyInitializationException`.
- **`FiltroJwt` NO lleva `@Component`**, se instancia a mano en
  `ConfiguracionSeguridad`. Como bean, Spring Boot lo registraria tambien en la
  cadena de filtros del servlet y correria dos veces por request, en silencio.
- **El login tarda siempre lo mismo.** Verifica contra un hash senuelo cuando el
  email no existe, para que la diferencia de tiempo no delate que cuentas estan
  registradas. Y los dos casos devuelven el mismo mensaje.
- **`UserDetailsServiceAutoConfiguration` esta excluida** en
  `BackendApplication`. Si no, Boot crea un usuario en memoria e imprime su
  contrasena en cada arranque, sin que nada la use. Ojo que en Boot 4 la clase
  esta en `org.springframework.boot.security.autoconfigure`, no donde dice
  internet.

### Registro cerrado con codigo de invitacion
`POST /auth/registro` exige un codigo que sale de `CODIGO_INVITACION`. El backend
va a estar publico, y sin eso cualquiera que encuentre la URL se crearia una
cuenta.

El primero que se registra crea el grupo; el segundo se suma; **un tercero se
rechaza**, porque el modelo de reparto asume dos integrantes.

Los usuarios ya NO se siembran por SQL: los crea la API, que es lo unico que sabe
hashear con BCrypt.

### `descripcion` es obligatoria
La usuaria la eligio como uno de sus tres campos: "algo que me recuerde el
momento". Es lo que le permite distinguir despues el gasto evitable del que no lo
era. No es decorativa.

### Concurrencia: bloqueo optimista con `@Version`
`Gasto` tiene un campo `@Version`. Hibernate agrega `AND version = ?` a cada
UPDATE. Si los dos integrantes editan el mismo gasto a la vez, la segunda
escritura falla en vez de pisar la primera en silencio.

### Schema: no hay. Es MongoDB.

**Esta es la perdida mas seria del cambio de base, y conviene poder nombrarla.**

Con Postgres habia migraciones versionadas con checksum en `db/migration`, mas
`ddl-auto=validate`: si alguien agregaba un campo a una entidad y se olvidaba la
migracion, **la app no arrancaba**. Un error se convertia en un deploy fallido,
que es el mejor lugar para que aparezca.

Mongo no tiene esquema. Un campo que se agrega y no se migra simplemente llega
`null` en produccion, y te enteras tres pantallas mas alla cuando algo se rompe
raro. Lo que quedo en su lugar:

- **`SembradorDeCategorias`** siembra las seis categorias al arrancar. Es
  idempotente porque esta escrito idempotente, no porque nada lo controle.
- **`auto-index-creation=true`** crea los indices declarados con `@Indexed` y
  `@CompoundIndex`. Crea indices; no valida la forma de los documentos.
- **`ValidacionDeConfiguracion`** ahora chequea que `MONGO_URI` no apunte a
  localhost en produccion. No reemplaza a Flyway, pero tapa un agujero nuevo: sin
  eso la app arrancaria contra una base vacia sin dar un solo error.

Si algun dia hacen falta migraciones de datos de verdad, la herramienta del
ecosistema es **Mongock**.

### El modelado: snapshots embebidos, y lo que cuestan
`Gasto` guarda `pagadoPor` y `categoria` como **documentos embebidos**
(`{id, nombre}` y `{id, nombre, icono}`), no como referencias.

Lo que se gana: la consulta mas frecuente de la app —- listar los gastos del mes
-— es una sola lectura, sin `$lookup` y sin N+1 posible. Ademas se resolvio solo
el pendiente de que `password_hash` viajara en cada listado, porque el snapshot
no lo incluye por construccion.

Lo que se paga: **si se renombra una categoria, los gastos viejos conservan el
nombre viejo.** Se acepta porque son las seis palabras que uso la usuaria y no
cambian. Si cambiaran, las salidas son un `updateMany` o asumir el snapshot como
dato historico ("asi se llamaba cuando lo cargo").

### No hay `@Transactional` en ningun servicio
No es un olvido. Mongo tiene transacciones multi-documento, pero exigen un
replica set y un `MongoTransactionManager` que Spring Boot no crea solo — y aca
casi todos los metodos escriben **un solo documento**, que en Mongo ya es atomico.

La diferencia de fondo con Postgres: alla `@Transactional` no servia solo para
atomicidad, abria la sesion de Hibernate, y de ahi salian el dirty checking y las
relaciones lazy. Aca lo que se lee es un objeto comun de Java: si querés que un
cambio se guarde, llamás a `save()`. Menos magia, y menos cosas que pasan sin que
las hayas pedido.

**El unico lugar que escribe dos documentos es el registro** (crea el grupo y el
usuario). Si falla en el medio queda un grupo huerfano; el reintento se cura solo
porque el segundo intento encuentra el grupo existente.

### Fechas como texto ISO, montos como Decimal128
Dos conversiones declaradas a mano en `ConfiguracionMongo`, y las dos importan:

- **`LocalDate` -> `"2026-09-07"`.** Mongo no tiene "fecha sin hora": su tipo
  Date es un instante UTC, o sea el mismo bug de zona horaria que motivo el bean
  `Clock`. Como texto, el dia es el dia. No se pierde nada al consultar porque
  **las fechas ISO ordenan igual como texto que como fecha**, asi que el rango
  semiabierto `[desde, hasta)` sigue funcionando con `$gte`/`$lt`.
- **`BigDecimal` -> `Decimal128`.** Es el analogo de `NUMERIC(12,2)`: decimal
  exacto. Sin declararlo, Spring Data lo guarda como String (no se puede sumar) o
  como Double (y vuelve el problema del centavo). Importa el doble porque el
  resumen y el saldo suman con `$sum` **adentro de Mongo**.

### Endurecimiento previo al deploy
- **Rate limiting** en `/auth/login` y `/auth/registro` (`LimitadorDeIntentos`,
  en memoria). Dos umbrales distintos a proposito: **5 por cuenta, 20 por IP**.
  Viole y Franco comparten wifi, asi que con el mismo limite ella olvidandose la
  contrasena cinco veces lo dejaria a el afuera. El de cuenta es el que protege
  de verdad: para atacar a alguien hay que mandar SU email, y eso no se puede
  falsear. El mapa tiene tope de claves, si no el limitador seria un vector de
  denegacion de servicio.
- **Politica de contrasenas** (`PoliticaDeContrasenas`): minimo 12 caracteres y
  **ninguna regla de composicion**, siguiendo NIST SP 800-63B. Exigir mayuscula,
  numero y simbolo empuja a la gente hacia "Password1!". Rechaza las comunes,
  las de pocos caracteres distintos, y las que contienen el nombre o el email de
  la propia persona.
- **Revocacion de tokens**: `usuario.token_version` viaja como claim `tv` en el
  JWT y se compara contra la base en cada request. `POST /auth/cerrar-sesiones`
  lo incrementa. Un token con firma valida y sin expirar se rechaza igual si su
  generacion quedo vieja. Es el boton de "perdi el celular", y es por usuario.
- **Perfil `produccion`**: activa `application-produccion.properties` (apaga el
  log de SQL, sin stacktrace ni mensaje interno en los errores) y
  `ValidacionDeConfiguracion`, que **impide arrancar** con el secreto o el codigo
  de invitacion de desarrollo. Verificado corriendo la imagen: sin `JWT_SECRETO`
  el contenedor sale con codigo 1.
- **Postgres local escucha solo en 127.0.0.1**, no en todas las interfaces.

### Lo que NO aplica a esta arquitectura
Aparece seguido en checklists genericos de seguridad y conviene saber por que no
va:

- **"API keys expuestas en el frontend"**: no hay ninguna API key. Eso es del
  mundo Supabase/Firebase, donde el navegador habla directo con la base usando
  una clave publica de la app. Aca el cliente solo guarda un JWT que esa persona
  se gano logueandose.
- **RLS (Row Level Security)**: Supabase lo necesita porque el cliente hace las
  consultas. Aca nadie mas que el backend se conecta a Postgres, y el control
  equivalente son los `WHERE` de los repositorios, que si estan. Agregarlo
  defenderia contra inyeccion SQL (no hay: cero concatenacion en las consultas) o
  contra una app comprometida, que se conectaria con el mismo usuario de base que
  RLS usaria.

Donde SI aplica la idea de "credencial en el cliente": **como guarda el token la
app mobile**. `AsyncStorage` no esta cifrado; va `expo-secure-store`, que usa el
Keychain. Pendiente para la sesion 6.

### Los agregados se calculan al vuelo, no se materializan
El saldo y el resumen no se guardan en ningun lado: son un `SUM` sobre el indice
`idx_gasto_grupo_fecha` en cada consulta.

Los numeros que cerraron la discusion: dos personas, ~15.000 filas despues de
cinco anios, ~3 ms por consulta, ~40 lecturas por dia. **0,12 segundos de trabajo
de base por dia** es todo el problema que materializar vendria a resolver.

A cambio, materializar costaria mantener deltas correctos en tres caminos de
escritura (alta, edicion, baja), donde un error corrompe el saldo para siempre y
en silencio; y necesitaria un job de reconciliacion cuya unica funcion seria
recalcular al vuelo para verificar que la optimizacion no mintio.

Nota que vale para una entrevista: **la opcion al vuelo es barata por la decision
de la sesion 1.** Como `montoPagador` ya viene resuelto en la fila, el saldo es un
`SUM` puro, sin division ni logica por fila. Con un porcentaje guardado, el
agregado tendria aritmetica por fila y el caso para materializar seria mas fuerte.

Cuando reverlo: si el grupo creciera mucho, si hubiera una pantalla de historico
que pida el saldo de todos los meses de una, o si las lecturas pasaran a miles
por segundo. Nada de eso esta cerca.

### El saldo es del mes, no historico
`GET /saldo` cubre solo el mes pedido. El alcance original decia "saldo actual"
sobre toda la historia, pero **no hay forma de saldar la cuenta**: sin una entidad
de liquidacion, un saldo historico solo crece y a los pocos meses es un numero
grande que no representa nada real.

Acotarlo al mes lo mantiene chico y accionable, al costo de asumir que se
arreglan mes a mes. Si algun dia quieren llevar la cuenta en serio, la solucion es
una entidad `Liquidacion(grupo, de, para, monto, fecha)` y `saldo = deudas - pagos`.

### El animo de la nutria: tendencia, tres estados
`CONTENTA` / `TRANQUILA` / `PREOCUPADA`, calculado en el backend.

Compara el gasto hormiga del **tramo transcurrido** del mes contra el **mismo
tramo** del mes anterior (6 dias contra 6 dias, no 6 contra 31). Baja de 10% o mas
-> CONTENTA; sube 10% o mas -> PREOCUPADA; en el medio -> TRANQUILA. Cero hormiga
-> CONTENTA. Sin datos del mes anterior -> TRANQUILA: la nutria no juzga el primer
mes de uso.

Es una medida relativa porque los ingresos son irregulares, y es contra el pasado
y no contra una meta porque el objetivo declarado de la usuaria es **bajar**, no
estar debajo de una linea. La banda de +-10% evita que cambie de humor por ruido.

La logica vive en `CalculadorDeAnimo` y `Periodo`, dos clases puras sin Spring ni
base, para que se puedan testear barato. Son el 100% de la cobertura de tests.

### Hay un `Clock` inyectable, y tiene zona horaria
`BackendApplication` declara un bean `Clock` en vez de usar `LocalDate.now()`
suelto. Dos motivos: un test puede fijar "hoy", y **el corte de mes depende de la
zona**. Un gasto cargado 21:00 del 30 de septiembre en Buenos Aires ya es 1 de
octubre en UTC, y Railway y Render corren en UTC. Configurable por
`app.zona-horaria`, default `America/Argentina/Buenos_Aires`.

### Los iconos: Metro no hace tree-shaking de un barrel
Los iconos de categoria son de **Lucide** (`lucide-react-native` sobre
`react-native-svg`), porque el campo `icono` que siembra el backend ya guarda
nombres de Lucide: cualquier otra libreria habria obligado a una tabla de
traduccion, o sea un segundo sistema de nombres para mantener a mano.

Lo que importa de verdad, y se midio: **cada icono se importa por su subpath**
(`lucide-react-native/icons/car`) y NO del barrel (`from 'lucide-react-native'`).
Importando del barrel, el bundle de iOS pesa **4,55 MB**; con los subpaths,
**2,63 MB**. Casi la mitad, porque Metro no hace tree-shaking de un barrel ESM y
se lleva los ~1500 iconos de la libreria aunque se usen seis.

Se verifico buscando dentro del `.hbc` iconos que la app no usa: con el barrel
estaban ahi.

Corolario que vale mas que el caso: **en React Native no se asume que el bundler
descarta lo que no se usa.** Un `import { X } from 'libreria'` de una libreria
grande merece que alguien mire cuanto pesa el bundle antes y despues.

### Pendiente de decidir
- **Cuando hacer obligatorio el `version` en el PUT.** Hoy es opcional: si el
  cliente lo manda, se verifica; si no, gana la ultima escritura. Conviene
  volverlo obligatorio cuando la app mobile este armada y sepamos que siempre lo
  reenvia.
- **El default 50/50 del reparto puede no ser lo justo para ellos**, porque tienen
  ingresos diferentes y separados. Es una conversacion entre ellos, no una
  decision tecnica.

## Estructura

```
/backend      Spring Boot
  src/main/java/com/gastoscompartidos/
    modelo/       entidades JPA
    repositorio/  interfaces de Spring Data
    servicio/     logica de negocio, unico lugar con reglas
    controlador/  endpoints REST, finitos: reciben, delegan, devuelven
    dto/          records de entrada y salida. La API NUNCA expone entidades
    seguridad/    JWT, filtro, config de Spring Security y UsuarioActual
    error/        excepciones de dominio + @RestControllerAdvice
    config/       conversores de Mongo y el sembrador de categorias
/mobile       Expo. Por features, no por capas: ver docs/diseno.md
/web          React + Vite (despues del MVP)
docker-compose.yml       MongoDB local
scripts/
  smoke-test.ps1         57 chequeos de la API contra el backend corriendo
```

Nombres de dominio en espanol (Gasto, Usuario, Grupo, Categoria), consistente
con el lenguaje del producto.

## Plan por sesiones

- [x] **1 — Modelo de datos y setup.** Proyecto Spring Boot, entidades JPA,
      Postgres local con Docker.
- [x] **2 — CRUD de gastos.** `GET /categorias`, `POST/GET/PUT/DELETE /gastos`
      con filtros por mes, categoria y pagador. Validaciones en tres capas,
      regla de visibilidad dentro del WHERE, y el reparto calculado en el
      backend. Verificado con `scripts/smoke-test.ps1` (29 chequeos en verde).
- [x] **3 — Los agregados: resumen y saldo.** `GET /gastos/resumen?mes=` (seccion
      personal: mi parte del mes, por categoria, total hormiga y el animo de la
      nutria) y `GET /saldo?mes=` (seccion pareja). Calculados al vuelo. Mas 15
      tests unitarios puros sobre `Periodo` y `CalculadorDeAnimo`, y el smoke
      test extendido a 44 chequeos.
- [x] **4 — Autenticacion.** Spring Security + FiltroJwt propio, BCrypt,
      registro cerrado con codigo de invitacion. UsuarioActualPorHeader se
      reemplazo por UsuarioActualPorJwt sin tocar ningun servicio ni
      controlador. Smoke test extendido a 57 chequeos.
- [x] **5 — Deploy.** Railway con el Dockerfile multi-stage, Postgres gestionado
      y Flyway aplicando las migraciones al arrancar. Verificado contra el
      dominio publico: `/actuator/health` en UP y sin `components`, login con
      JWT, alta de gasto, resumen con el animo de la nutria, y baja. El bean
      `Clock` quedo probado en serio: el contenedor corre en UTC y el corte de
      mes igual cayo en la fecha de Buenos Aires. Ver **`docs/deploy.md`**.
- [~] **6 — App Expo minima.** Contra la API deployada, no localhost. Estan el
      login, el resumen con el animo de la nutria, el alta de gasto y **la lista
      de gastos del mes**, que es donde se ve la marca de hormiga gasto por
      gasto. Los iconos de categoria ya se dibujan con Lucide y no como texto.

      **Para cerrar la sesion falta la seccion de pareja**, que son dos cosas
      encadenadas: el alta hoy manda `tipo: 'PERSONAL'` fijo, asi que sin poder
      cargar un COMPARTIDO la pantalla de saldo mostraria cero siempre. Primero
      el tipo compartido en el alta (con el reparto), despues `/saldo`.

      Lo que queda afuera y no bloquea el cierre: editar y borrar, y los filtros
      por categoria y pagador del listado (el endpoint ya los acepta).

      Bug conocido: el numero grande del resumen se parte en dos lineas cuando
      no entra.

      Nada de esto se probo en un telefono todavia: se verifico con
      `tsc --noEmit` y con `expo export`, que bundlea de verdad.
- [x] **6.5 — Migracion a MongoDB.** El motivo es de busqueda laboral: la
      postulacion pide relacional y no relacional, y MatchPoint ya cubre
      Postgres. Se rehicieron modelo, repositorios y los dos servicios que tocan
      agregados. **No se toco `seguridad/` ni `error/`, y `CalculadorDeAnimo` y
      `Periodo` siguen igual** —- los 24 tests puros pasaron sin cambios. Ver las
      secciones de modelado, transacciones y conversores mas arriba.
- [ ] **6.6 — Salir de Railway.** El credito de $5 se acaba y no hay tier gratis
      permanente. Destino elegido: **Render (Docker) + MongoDB Atlas M0**, los dos
      gratis, con un cron externo pegandole cada 10 minutos para que el servicio
      no se duerma. El arranque en frio de Spring Boot es de 40-60s, y la app
      tiene un requisito duro de velocidad de carga: el ping es lo que hace que
      Viole nunca se lo coma. Atlas y Render **en la misma region**.
- [ ] **7 — Build EAS y TestFlight.**

## Comandos

> **El autor trabaja en PowerShell 5.1 en Windows.** Ahi NO funcionan `&&`,
> `||`, ni `<` para redirigir entrada, ni los operadores `?:` y `??`. Darle
> siempre los comandos en sintaxis compatible: `;` para encadenar,
> `Get-Content archivo | comando` en vez de `comando < archivo`, y `.\mvnw.cmd`
> en vez de `./mvnw`. Ojo tambien con `curl`, que en PowerShell es un alias de
> `Invoke-WebRequest`: para el curl de verdad va `curl.exe`.

```powershell
# Levantar MongoDB
docker compose up -d

# Las categorias las siembra SembradorDeCategorias al arrancar la app.
# Los usuarios, POST /auth/registro. No hay script de seed que correr.

# Levantar la API
cd backend; .\mvnw.cmd spring-boot:run

# Correr los tests
cd backend; .\mvnw.cmd test

# Consola de MongoDB
docker exec -it gastos-mongo mongosh -u gastos -p gastos_local --authenticationDatabase admin gastos
```
