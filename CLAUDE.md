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
hormiga), y desde la v1.0 la descripcion es opcional. Cada campo extra que se le
agregue al formulario se paga en abandono.

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
| Mobile   | Expo + React Native + TypeScript              | en curso |
| Web      | React + Vite + TypeScript + Tailwind          | pendiente |
| Deploy   | Render (Docker) + MongoDB Atlas M0            | en curso |

Build con el **Maven wrapper** (`./mvnw`, `mvnw.cmd`): no hace falta instalar
Maven, el script baja la version que el proyecto declara.

> Nota: el plan original decia Spring Boot 3, pero 3.x ya salio de soporte y
> Initializr solo ofrece 4.x. Vamos con 4.1.1. Impacto practico: mucha
> documentacion y muchos tutoriales de internet siguen siendo de Boot 3.
> Diferencias visibles: el starter web ahora es `spring-boot-starter-webmvc`
> (antes `spring-boot-starter-web`) y los starters de test estan separados por
> modulo (antes uno solo, `spring-boot-starter-test`). Las anotaciones del dia
> a dia (`@RestController`, `@Service`, `@Repository`, Spring Data) no
> cambiaron. Ojo que esta nota es de cuando el backend era Postgres: `@Entity`
> se fue con la migracion a Mongo en la 6.5.

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

### Registro abierto, un grupo por cuenta (v1.0)
Hasta la v1.0 el registro exigia `CODIGO_INVITACION`: habia un unico grupo en
la base, el primero lo creaba y el segundo se sumaba. **Para publicar en la App
Store eso se saco**, porque una app publicada tiene que dejar registrarse a
cualquiera que la baje.

Sacar el codigo no era solo abrir el formulario. Con un unico grupo, un
desconocido que baja la app caeria en el grupo de Viole y Franco. Ahora **cada
registro crea su propio grupo, de un integrante**, y sumarse al de otra persona
queda para la v1.1 (codigos por grupo, y ahi vuelve el tope de dos). **Hecho en
la 2.1**, ver mas abajo.

Lo que arrastro, que es lo interesante:
- **Un grupo de uno no puede compartir.** `UsuarioRepositorio.tienePareja()` es
  la regla, en un solo lugar: sin pareja se rechaza crear un COMPARTIDO, volver
  COMPARTIDO un PERSONAL, y abrir una vaquita. Un COMPARTIDO que ya existia se
  sigue pudiendo corregir (el caso de quien queda sola cuando la otra persona
  borra su cuenta). La app esconde esas opciones leyendo `tienePareja` del
  resumen -- viaja ahi y no en un endpoint aparte para no sumar una request a la
  pantalla que mas se abre -- y lo lee como `!== false`, para que la app nueva
  contra un backend viejo no les esconda la seccion de pareja.
- **El rate limit del registro cuenta los exitos.** Antes solo contaba codigos
  equivocados; sin codigo no hay fallo, y el abuso pasa a ser tener exito muchas
  veces. 5 por IP cada 15 minutos (`app.registro.max-por-ip`), configurable solo
  para que el smoke test pueda registrar varios usuarios en local.
- **Riesgo conocido: el registro revela que emails tienen cuenta**, porque
  contesta "Ese email ya esta registrado". Antes habia que tener el codigo para
  llegar a esa respuesta. El arreglo de verdad es verificar el email (mandar un
  mail en vez de contestar en pantalla), que es infraestructura nueva. Mientras,
  el tope por IP frena el barrido masivo.
- **El grupo huerfano ya no se cura solo.** Si falla entre crear el grupo y el
  usuario, el reintento crea otro grupo. Queda un documento vacio que nadie ve.
- **El backend se deploya antes que la app.** La app nueva no manda el codigo y
  el backend viejo lo exigia con `@NotBlank`. Al reves no pasa nada: el
  backend ignora campos que no conoce.
- **El smoke test junta a Franco y Ella por `mongosh`**, porque la API ya no
  sabe hacerlo. Es el mismo estado que tienen Viole y Franco en produccion.

Los usuarios ya NO se siembran por SQL: los crea la API, que es lo unico que sabe
hashear con BCrypt.

### Sumarse a un grupo: codigos de invitacion (v1.1, seccion 2.1)
Tres endpoints en `GrupoServicio`/`GrupoControlador`: `POST /grupo/invitar`
genera un codigo, `POST /grupo/sumarse` lo consume, `POST /grupo/salir` deja
el grupo compartido. El tope sigue en **dos integrantes** -- subirlo es 2.2,
el cambio de modelo mas caro, y no hacia falta adelantarlo para esto.

**Por que "salir" es simetrica y no hay un "expulsar".** La pregunta que lo
disparo: que pasa si a la pareja le va mal y ya no quieren estar en el mismo
grupo. Con el tope en dos, "me voy yo" y "te saco a vos" llegan al MISMO
estado final -- la otra persona queda sola -- asi que no hace falta la
segunda accion. Y conviene que no exista: en una pelea, darle a una persona
el boton de sacar a la otra del grupo compartido abre la puerta a usarlo
como forma de control, no solo como limite prolijo. Que cualquiera pueda
salir, sin pedirle permiso a nadie, evita ese problema de raiz. (Si algun
dia el tope sube con 2.2, esto hay que revisarlo: con tres o mas, "me voy"
y "expulso a alguien" dejan de ser la misma accion.)

**El codigo de invitacion NO se guarda hasheado**, a diferencia del codigo de
reseteo de contrasena (`Usuario.resetCodigoHash`), y es a proposito:
- El de reseteo tiene que resistir fuerza bruta con solo 6 digitos (un millon
  de combinaciones); el hash mas el tope de 5 intentos son la defensa real.
- Quien se suma no sabe de que grupo es el codigo -- a diferencia del reseteo,
  donde el email ya identifica al usuario y el codigo solo se compara contra
  EL SUYO -- asi que hace falta poder buscar el grupo directo por el valor del
  codigo. Hasheado, esa busqueda seria un escaneo de todos los grupos con
  invitacion activa (a esta escala no importaria, pero es un patron raro para
  no ganar nada).
- La defensa acá es la ENTROPIA: 8 caracteres de un alfabeto de 32 (sin
  `0/O` ni `1/I/L`, que se confunden al leerlos o copiarlos) son ~2^40
  combinaciones. Vence a los 7 dias, y el rate limit por cuenta en
  `/grupo/sumarse` es defensa en profundidad, no la proteccion principal.

**Consumir el codigo es atomico**, mismo patron UPDATE...WHERE que
`PozoConsultasImpl.agregarAporte`: `GrupoConsultasImpl.consumirInvitacion`
hace un `findAndModify` que busca por `codigo + vigencia` Y BORRA el codigo
en la misma operacion. Si dos requests llegaran con el mismo codigo casi
juntas, el filtro de la segunda ya no matchea nada -- no hace falta un lock
aparte. La diferencia con `agregarAporte` es que aca si hace falta el
documento (el id del grupo al que sumar a quien se une), asi que es
`findAndModify` y no `updateFirst`: por default devuelve el documento COMO
ESTABA ANTES del update.

**`sumarse` y `salir` comparten `GastoConsultas.moverPersonalesA`**: los
gastos PERSONAL de la persona la siguen a donde vaya, en las dos
direcciones. Es deliberadamente MAS SIMPLE que
`CuentaServicio.borrar()`: alla hay que anonimizar, porque la cuenta deja de
existir. Aca la cuenta sigue existiendo -- solo cambia de grupo -- asi que
los COMPARTIDO y los aportes a la vaquita que quedan atras NO se tocan: se
ven con el nombre real, porque la persona real sigue siendo quien es. Solo
"Cuenta eliminada" implica una cuenta borrada.

**La pantalla en mobile tambien esta.** `app/grupo.tsx` junta invitar y
sumarse en una sola pantalla con dos modos (mismo espiritu que la vaquita:
el estado depende de lo que la persona elige, no de una pantalla nueva por
camino), con "Compartir" usando el `Share` de React Native -- sin agregar
ninguna dependencia nueva, alcanza para mandarlo por WhatsApp.
`app/salir-del-grupo.tsx` es el mismo molde que `borrar-cuenta.tsx` (explicar,
pedir contrasena, confirmar con un Alert) con otro final: la cuenta sigue
existiendo, asi que no hay nada que anonimizar. `Ajustes` decide sola, con
`GET /grupo`, cual de las dos ofrecer.

De paso se encontro y se corrigio un comentario en `Campo.tsx` que predecia
mal el futuro: preveia que el codigo de invitacion de la v1.1 iba a
necesitar `capitalizar={false}` por el mismo bug que el codigo viejo (iOS
poniendole mayuscula a la primera letra mientras se escribe). No hizo
falta: el backend normaliza a mayusculas antes de comparar, asi que da
igual como salga tipeado.

**Y probando en el telefono de verdad salio que la asuncion de arriba estaba
mal.** La primera version de "invitar" solo tenia "Compartir", asumiendo que
alcanzaba con la hoja de compartir de iOS, que ya trae "Copiar". Franco lo
probo por Expo Go y encontro que hace falta poder copiar directo, sin pasar
por esa hoja. Se agrego un boton "Copiar codigo" al lado, con
`expo-clipboard` -- la unica dependencia nueva de la sesion 2.1, instalada en
`~57.0.2` para seguir la misma convencion de version que el resto de los
paquetes `expo-*` de este SDK. `npx expo install` no funciono desde el
sandbox de la nube (la misma clase de bloqueo de red que ya afecto a otras
herramientas de Expo), asi que se instalo con `npm install expo-clipboard@~57.0.2`
directo y se verifico con `npm ci` en una carpeta aparte -- el mismo chequeo
que ya delato el problema de lockfile de la 6.11, para no repetirlo.

**Se hizo desde una sesion en la nube**, sin Docker ni Mongo local, y sin
telefono a mano. Verificado ahi: el backend compila y los 9 tests nuevos de
`GrupoServicioTest` (mocks, sin base) pasan junto con los 144 que ya habia; el
mobile con `tsc --noEmit` (tambien con `--noUnusedLocals`) y `expo export`, que
bundlea de verdad. Lo que NINGUNA de esas dos cosas prueba: que el indice unico
parcial de `invitacion_codigo` exista de verdad, que `findAndModify` sea
atomico contra una base real, y que las pantallas nuevas se vean y se usen bien
en un telefono de verdad.

**Los tres se cerraron despues, con PC y telefono.** `scripts/smoke-test.ps1`
corrio contra Mongo local (Docker) y dio "TODO OK", incluida la seccion 14
("Sumarse a un grupo, y salir") -- confirma el indice y el `findAndModify` de
verdad. Y Franco probo invitar/sumarse/salir por Expo Go en su telefono: anduvo
bien, salvo el hallazgo de copiar codigo que ya esta contado mas arriba. Con
eso, la seccion 2.1 queda verificada en las tres capas que este documento pide
(regla 1: cerrar con algo corriendo y probado).

### Borrar la cuenta (v1.0)
Lo exige la App Store (guideline 5.1.1(v)): si la app deja crear cuenta, tiene
que dejar borrarla desde adentro, y borrarla de verdad. `POST /auth/borrar-cuenta`
con la contrasena, desde Ajustes -> Borrar mi cuenta. Es exactamente lo que
promete la politica de privacidad (publicada en Notion, linkeada desde Ajustes):

- Los PERSONAL de quien se va se **borran**.
- Los COMPARTIDO que pago y sus aportes a vaquitas **quedan**, porque son el
  historial de la otra persona, con el nombre cambiado a "Cuenta eliminada". Se
  cambia solo el nombre del snapshot, NO el `usuarioId`: el saldo y la edicion
  comparan por id. La descripcion queda.
- Si era el ultimo del grupo, se va todo: gastos, vaquitas y grupo.

Decisiones que vale la pena poder explicar:
- **POST y no DELETE**: lleva la contrasena en el cuerpo, y un cuerpo en un DELETE
  no tiene semantica definida en HTTP.
- **Contrasena equivocada = 400, no 401.** La app cierra sesion sola ante un 401
  con token (asi detecta el token vencido); equivocarse al confirmar no puede
  sacarte de la app. Y tiene rate limit por cuenta: con un token robado, si no,
  seria un verificador de contrasenas.
- **El usuario se borra al final.** Son varias escrituras sin transaccion; todos
  los pasos son idempotentes, asi que si algo falla la cuenta sigue viva y se
  reintenta. Al reves quedarian datos sin duenio y nadie con token para
  terminar.
- **Los tokens no hay que revocarlos**: `UsuarioActualPorJwt` busca al usuario en
  cada request, y el de una cuenta borrada da 401 solo, en todos sus telefonos.
- **Los aportes se anonimizan con `arrayFilters`** (`aportes.$[a].usuario.nombre`).
  El `$` posicional a secas toca solo el PRIMER elemento que matchea, y una
  persona aporta mas de una vez. El smoke test lo verifica con dos aportes.
- **Dos cosas que el borrado rompia y hubo que arreglar antes**: editar un
  compartido cuyo pagador ya no existe (buscaba al usuario y tiraba "No existe")
  ahora conserva el snapshot; y `porPersona` de la vaquita listaba solo a los
  integrantes actuales, asi que los aportes de quien se fue sumaban en el total
  pero no aparecian en ninguna fila.
- **En la app, borrar la cuenta vacia la cola offline** (`MotivoDeSalida`
  `'cuenta-borrada'`), y la pantalla avisa cuantos gastos sin mandar se perderian.
  La cola ahora se conserva SOLO con el token vencido: la excepcion es esa, no
  la regla.
- **Sin nutria en la pantalla de borrado.** Una nutria triste ahi seria usar al
  personaje para hacer sentir culpa a quien se quiere ir.

### Olvidé mi contraseña: un código por mail, por la API de Gmail (v1.0)
`POST /auth/olvide-contrasena` manda un código de seis dígitos al mail;
`POST /auth/restablecer-contrasena` lo verifica con la contraseña nueva y deja
adentro. Pantalla `app/recuperar.tsx`, desde el login.

**Por dónde salen los mails fue la decisión difícil, y conviene poder contarla**
(detalle en `docs/mails.md`). La restricción era no pagar nada:
- **SMTP no**: Render gratis bloquea los puertos SMTP desde septiembre de 2025.
- **Resend, Brevo, etc. no**: sin dominio propio no pueden autenticar un remitente
  `@gmail.com`, y el mail termina en spam o rechazado por DMARC.
- **Firebase Auth no**: resetea usuarios de Firebase, y los de acá viven en Mongo
  con BCrypt. Habría que mudar todo el login por un botón.
- **Sí: la API REST de Gmail**, por HTTPS, desde la cuenta de Franco. Sale firmada
  por Google, llega a la bandeja, y es gratis. El precio es OAuth: un refresh
  token con el scope `gmail.send` (solo mandar), con el proyecto de Google Cloud
  **en producción** -- en "prueba" el token vence a los 7 días.

`EnviadorDeMails` es una interfaz, como `UsuarioActual`: `EnviadorPorGmail` si
están las credenciales, `EnviadorPorLog` si no (el código queda en el log, que
sirve para ayudar a mano y para desarrollo). No frena el arranque si faltan: la
app anda entera sin mails.

Las reglas, cada una por un ataque concreto:
- **El pedido responde 204 exista o no el email**, y todos los errores del código
  dicen lo mismo: no es un verificador de cuentas.
- **Vence a los 15 minutos y acepta 5 intentos**: un millón de combinaciones, y la
  chance de adivinar queda en 1 en 200.000 por código pedido.
- **Pedir códigos tiene tope por email y por IP**: si no, se le inunda la casilla
  a alguien, o se usa la cuenta de Gmail de la app para mandar spam.
- **El código se guarda con BCrypt**, no con un hash rápido: seis dígitos se
  recorren enteros en milisegundos con SHA-256 si la base se filtra.
- **Restablecer cierra las otras sesiones** (`token_version`), porque muchas veces
  se resetea justo porque alguien más entró.
- **La política de contraseñas va después del código y no gasta intentos**:
  elegir una contraseña corta no es adivinar códigos.
- **El mail que no sale se loguea y no se informa**: un error distinto revelaría
  que la cuenta existe.
- Código y no link: un link a `minutria://` lo bloquean muchos clientes de mail,
  y hacerlo bien pide Universal Links, o sea dominio propio. El campo del código
  usa `autoComplete="one-time-code"`, así iOS lo ofrece desde la app de Mail.

### `/auth/**` dejaba pasar de mas, y una auditoria de seguridad lo encontro
`cerrar-sesiones` y `borrar-cuenta` viven bajo `/auth/`, pero a diferencia de
`registro`/`login`/`olvide-contrasena`/`restablecer-contrasena` SI exigen
token. Hasta ahora, `.requestMatchers("/auth/**").permitAll()` los dejaba
pasar igual en la capa de Spring Security: quedaban protegidos solo porque
sus servicios llaman a `UsuarioActual.requerido()`. Andaba, pero era un solo
punto de falla -- un endpoint nuevo bajo `/auth/` que se olvidara esa llamada
quedaria abierto sin que nada lo frenara aca.

Ahora el `permitAll()` lista los cuatro paths que son publicos de verdad, y
`cerrar-sesiones`/`borrar-cuenta` caen en `.anyRequest().authenticated()`
como el resto de la API: la cadena de filtros los rechaza antes de llegar al
controlador, no solo el servicio despues.

Ningun test de servicio prueba esto -- llaman al servicio directo, sin pasar
por `ConfiguracionSeguridad`. Se intento un `@WebMvcTest`, pero
`@EnableMongoAuditing` (declarado en `BackendApplication`) arrastra beans de
Mongo al slice y lo rompe; seguir ese camino hubiera significado mockear medio
modulo de Mongo para probar una regla de seguridad. Se verifica como ya se
verificaba el 404-como-401: con el smoke test (`sin token, /auth/cerrar-sesiones
da 401` y `.../borrar-cuenta da 401`).

### El fallback de mails al log, y el mismo audit

Segundo hallazgo de la misma auditoria. Si a las credenciales de Gmail
(`ConfiguracionMails`) les falta cualquiera de las cuatro, la app cae a
`EnviadorPorLog`, que escribe el mail entero -- codigo de reseteo incluido --
en el log de Render. Es a proposito (ver la seccion de arriba), pero hasta
ahora el aviso era un `log.warn` igual en local que en produccion, y si las
credenciales se caen DESPUES de un tiempo funcionando (se revocan a los 6
meses sin uso, o si cambia la contrasena de la cuenta de Google), ese warning
de arranque -- que nadie vuelve a mirar -- era la unica senial. Podia durar
meses sin que nadie se entere de que los codigos de reseteo quedan en texto
plano en el log.

Ahora `enviadorDeMails` recibe el `Environment` y, con el perfil `produccion`
activo, el mismo caso loguea en **ERROR** con un mensaje explicito. Sigue sin
frenar el arranque -- eso seguiria siendo desproporcionado -- pero un ERROR en
produccion es la clase de linea que una alerta de logs si mira. La decision de
QUE enviador se devuelve no cambia, solo la severidad; `ConfiguracionMailsTest`
prueba lo primero (con `MockEnvironment`, sin necesitar Spring) y no lo
segundo, porque afirmar un nivel de log pediria capturar el appender de
Logback para una sola linea que se revisa leyendo el codigo.

### `descripcion` es opcional (desde la v1.0)
La usuaria la eligio como uno de sus tres campos: "algo que me recuerde el
momento". Es lo que le permite distinguir despues el gasto evitable del que no lo
era.

**Fue obligatoria hasta que Viole uso la app de verdad.** Parada en el mostrador,
tipear era el paso que mas frenaba, y muchas veces categoria y monto ya dicen todo
("cafe, $3.000"). Gano el requisito duro -- prefiere olvidarse un gasto antes que
anotar lento -- sobre un campo util pero no imprescindible. Es la clase de
decision que solo sale de probar con la persona, no de la entrevista.

Como quedo:
- **Ausente es `null`, nunca `""`.** `GastoServicio.descripcionDe` convierte vacia
  o solo espacios en null: una sola forma de decir "no tiene".
- **En la lista, sin descripcion la categoria sube a titulo** y sale del segundo
  renglon, para no decir "Cafe" dos veces en la misma fila.
- **La etiqueta del formulario dice "(opcional)" y no tiene placeholder**: el
  "Algo que te recuerde el momento" invitaba a llenarla.
- **Backend antes que app, otra vez**: la app nueva manda gastos sin descripcion,
  y el backend viejo los rechazaba con `@NotBlank`. Con la cola offline eso seria
  peor que un error: el gasto quedaria marcado como rechazado en el telefono.

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

**Los dos lugares que escriben varios documentos son el registro** (crea el grupo
y el usuario) **y el borrado de cuenta**. En el registro, si falla en el medio
queda un grupo vacio que nadie ve (desde la v1.0 el reintento ya no lo reusa). En
el borrado, el usuario va al final y todos los pasos son idempotentes, asi que un
fallo se cura reintentando.

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
  `ValidacionDeConfiguracion`, que **impide arrancar** con el secreto de
  desarrollo o una `MONGO_URI` que no sirve (el codigo de invitacion se fue con
  el registro abierto de la v1.0).

  **Y ahi hubo un bug que vale la pena tener presente.** Era un `@Component` con
  `@Profile("produccion")` y los chequeos en el constructor, pero **nadie depende
  de ese bean**: Spring lo creaba cuando le tocaba, y lo que le tocaba antes era
  la cadena que termina en `mongoTemplate`. Con la URI apuntando a una base que
  no existe -- justo el caso que el chequeo venia a cazar -- el driver de Mongo
  tiraba primero y el guardian no hablaba nunca. O sea que solo funcionaba
  cuando la URI ya era correcta, que es cuando menos falta hace.

  Ahora lo invoca `ValidacionAlArrancar`, un
  `ApplicationListener<ApplicationPreparedEvent>` registrado a mano en `main()`.
  Ese evento se publica al final de `prepareContext()`: el `Environment` ya esta
  completo y las definiciones de beans cargadas, pero `refresh()` todavia no
  corrio, asi que **no se instancio ningun singleton**. Es el ultimo momento en
  que se puede frenar el arranque sin que nada se haya conectado a ningun lado.
  Verificado corriendo el jar: cada variable que falta da su propio mensaje, en
  orden, y la URI de Atlas pegada tal cual (sin `/gastos`) se caza antes de
  tocar la red.
- **La base local escucha solo en 127.0.0.1**, no en todas las interfaces.

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
Keychain en iOS y el Keystore en Android. **Hecho en la sesion 6**, en
`mobile/src/almacenamiento/sesion.ts`. Limite conocido: SecureStore no existe en
web, asi que cuando llegue `web/` esa capa necesita otra implementacion.

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

**La sesion 6.7 construyo esa entidad, acotada a un viaje.** Un aporte a la
vaquita ES una liquidacion anticipada: pagar por adelantado gastos que todavia no
se hicieron. De ahi sale que sacar plata del pozo no genere deuda entre ellos, y
que el filtro `sinPozo()` saque los gastos del viaje del saldo, del total hormiga
y del conteo que alimenta a la nutria. Ver **`docs/vaquita.md`**.

### Saldar deudas: `Liquidacion`, sin acotar a un viaje (v1.1, seccion 2.3)
La version general de la entidad de arriba. `GET /saldo` (del mes) sigue
existiendo sin cambios; esto es aparte: `GET /saldo/total` (un numero
siempre vigente, "te deben $X" / "le debes $X"), `POST /saldo/liquidaciones`
("ya le pague esto"), y `GET /saldo/liquidaciones` (el historial). Viven en
`LiquidacionServicio`/`LiquidacionControlador`, servicio propio y no una
lectura mas de `ResumenServicio` -- mismo criterio que separo `PozoServicio`
de `GastoServicio`.

**Por que un numero solo y no un extracto mes a mes con arrastre**, que era
la otra forma de mostrar la misma cuenta: es la misma forma que ya usa la
vaquita (`restante = aportes - gastos`, un numero), y evita construir una
pantalla de estado de cuenta para una pareja de dos personas.

**`Liquidacion.de`/`para` son snapshots** (`ReferenciaUsuario`), igual que
`Gasto.pagadoPor`: si alguien sale del grupo (seccion 2.1) despues de una
liquidacion, esta sigue leyendose con el nombre de quien pago en ese momento.

**Inmutable, como `Aporte`, pero la correccion es distinta.** Un aporte mal
cargado se corrige con un monto NEGATIVO del mismo lado (`docs/vaquita.md`).
Una liquidacion no admite negativo (`@Positive`): si se cargo al reves, la
correccion es otra liquidacion con `de` y `para` invertidos. La diferencia de
fondo es que un aporte es un numero con signo desde un solo lado (cuanto
puso una persona), mientras que en una liquidacion lo que importa leer
despues es LA DIRECCION -- quien le pago a quien -- y un monto negativo ahi
se leeria raro ("Franco le pago -1000 a Viole").

**`saldoHistoricoDe` es `saldoDe` sin `enElPeriodo`**: la misma agregacion de
Mongo, sin el recorte de fechas, porque una deuda de hace ocho meses sigue
contando si nunca se liquido. Las liquidaciones en cambio se suman en Java,
no con un pipeline: se esperan pocas (un puñado por año), a diferencia de
los gastos.

**Se subio `otroIntegranteDe` a `UsuarioRepositorio`** como metodo default,
mismo lugar que `tienePareja`: `ResumenServicio` y `LiquidacionServicio`
necesitaban la misma regla ("la otra persona del grupo, o null"), y tenerla
en dos servicios es tenerla en dos lugares que se pueden desincronizar.

**Y ese refactor rompio dos tests que ya estaban en verde**, que vale la
pena contar porque es exactamente el tipo de cosa que un mock no avisa
sola: `ResumenServicioTest.meDeben` y `.debo` stubeaban
`findByGrupoIdOrderByIdAsc` (lo que el metodo privado viejo llamaba de
verdad, porque vivia en el servicio). Al mudar la logica a un metodo
default de la interfaz MOCKEADA, Mockito dejo de correr ese cuerpo -- no
hay stub, no hay comportamiento -- y las dos pruebas empezaron a fallar
(esperaban 505.00, daba 0.00). Se corrigio estubeando `otroIntegranteDe`
directo, igual que ya hacia falta con `tienePareja`. Ningun cambio de
produccion estaba mal; lo que estaba desactualizado era el test. Es la
misma leccion que "Los tests: dos capas" ya documenta sobre
`tienePareja`, aplicada en el momento en que se la volvio a pisar.

**`RegistrarLiquidacionRequest.meLoPagaron`, agregado disenando la pantalla
de mobile.** La primera version solo dejaba "yo pague" (`de` = quien manda
la request), copiando literal el razonamiento de `AporteRequest` ("nadie
puede aportar en nombre de otro"). Pero una liquidacion, a diferencia de un
aporte, es un hecho ENTRE DOS personas: cualquiera de las dos puede ser
quien abre la app para anotarlo, inclusive quien RECIBIO el pago. Sin este
campo, si Viole le paga a Franco en efectivo y es FRANCO quien agarra el
telefono para anotarlo, no habia forma de decir "me pagaron" -- el unico
boton disponible diria lo contrario de lo que paso. `meLoPagaron` (default
`false`, booleano primitivo y no `Boolean`: falta el campo en el JSON y
Jackson lo llena con el default de Java, no hace falta mandarlo siempre)
invierte `de`/`para` en el servicio. El smoke test lo prueba anotando el
MISMO pago desde los dos lados: Deudor con `meLoPagaron=false` para el
primero, Acreedor con `meLoPagaron=true` para el segundo, y los dos quedan
guardados igual (de Deudor, para Acreedor).

Verificado en el backend: 152 tests, todos en verde salvo `contextLoads`, y
`scripts/smoke-test.ps1` contra Mongo real (seccion 15).

**Y probarlo en el telefono encontro un bug real: `saldo.tsx` no se enteraba
de los pagos.** La pantalla "Gastos compartidos" (la seccion pareja principal,
`GET /saldo?mes=`) y "Saldar cuentas" (`GET /saldo/total`) son dos consultas
completamente separadas. Franco anoto que Viole le pago $4.000 de una deuda
de $5.000 -- "Saldar cuentas" bajo a $1.000, como corresponde -- pero al
volver a "Gastos compartidos" seguia diciendo que Viole le debia $5.000. La
causa esta en `ResumenServicio.saldo()`: hace `gastos.saldoDe(...)` puro,
sin tocar `Liquidacion` para nada. No es un bug de escritura, es que esa
consulta nunca supo que las liquidaciones existen.

**La decision, discutida antes de tocar codigo (regla 3):** la razon
original para que el saldo fuera del mes y no historico era "no hay forma de
saldar la cuenta" (ver "El saldo es del mes, no historico" mas arriba) -- sin
`Liquidacion`, un numero historico solo crecia. Esa razon ya no existe: es
exactamente lo que esta seccion construyo. Restar las liquidaciones TAMBIEN
al saldo del mes se descarto por ambiguo (si la deuda abarca mas de un mes,
no hay un mes correcto al que restarle el pago). Se opto por lo mas simple y
lo unico sin ambiguedad: `app/saldo.tsx` -- la pantalla que dice "quien le
debe a quien" -- ahora muestra el MISMO numero que "Saldar cuentas"
(`GET /saldo/total`, ya neto de pagos), y dejo de pedir `GET /saldo?mes=`.
El selector de mes salio de esa pantalla (dejo de aplicar); "Ver los gastos
del mes" sigue, para ver el detalle. El endpoint `GET /saldo?mes=` sigue
existiendo en el backend sin tocarse -- "cuanto generaron los gastos de este
mes" sigue siendo una pregunta valida, solo que ya ninguna pantalla la hace.

Con eso, `traerSaldo()` y el tipo `SaldoRespuesta` quedaron sin ningun
llamador en el cliente y se borraron (`mobile/src/features/saldo/api.ts`,
`mobile/src/api/tipos.ts`): codigo muerto con un comentario que además ya
mentia ("sigue siendo el pulso del mes"), la misma clase de cosa que ya paso
con el comentario de `Campo.tsx` en la seccion 2.1.

Verificado con `tsc --noEmit --noUnusedLocals` y `expo export`. Falta
volver a probar esta pantalla en el telefono con el fix.

### Saldar deudas: editable, pero sin tocar la dirección

Pedido de Franco, mas tarde en el proyecto: que una liquidación tambien se
corrija tocando la fila, como ya pasó con `Ingreso` (sección 2.3c) y
`Aporte` (ver más abajo, "La vaquita: el aporte se edita y se borra de
verdad"). Antes de esto, `Liquidacion` no tenía NINGÚN mecanismo de
corrección -- ni siquiera el asiento-en-contrario que `Aporte` tenía: el
javadoc decía "se corrige con otra liquidación invertida", pero
`liquidaciones.tsx` no exponía ningún botón para hacerlo.

**Ganó un `setMonto()`, no un `id` nuevo**: a diferencia de `Ingreso` y
`Aporte` (records embebidos sin identidad propia), `Liquidacion` siempre
fue su propia colección top-level con `@Id`, así que editar/borrar es el
mismo patrón de siempre (`findById` + mutar + `save()`, como `Gasto`) y no
hizo falta ningún `$elemMatch` ni `$pull` -- los dos métodos nuevos del
repositorio (`findByIdAndGrupoId`, `deleteByIdAndGrupoId`) son derivados de
Spring Data, sin una línea de `MongoTemplate` a mano.

**La diferencia de fondo que SÍ se mantuvo: `EditarLiquidacionRequest` solo
tiene `monto`.** No hay forma de editar `de`/`para` por este endpoint -- ni
siquiera por accidente, porque el campo no existe en el DTO. Si una
liquidación se anotó al revés, la corrección sigue siendo borrarla y
registrarla de nuevo bien.

**Y el alcance es DISTINTO al de `Aporte`, a propósito.** Un aporte solo lo
puede tocar quien lo hizo (`usuario.usuarioId` en el filtro). Una
liquidación la puede editar o borrar CUALQUIERA de los dos integrantes del
grupo, no solo quien la registró -- el filtro es `findByIdAndGrupoId`, sin
usuario. La razón ya estaba en el diseño de `RegistrarLiquidacionRequest`:
`meLoPagaron` existe justamente porque cualquiera de los dos puede ser
quien abre la app para anotar un pago, inclusive quien lo recibió. No hay
un campo "quién lo registró" distinto de `de`/`para`, así que restringir la
corrección a una sola persona no tendría de qué agarrarse.

Verificado con el caso cruzado en los dos sentidos contra Mongo real:
Deudor y Acreedor registran un pago cada uno ($200 y $300). Deudor edita el
pago que registró ACREEDOR (300 → 250): la dirección no se toca, y el
saldo se recalcula solo (quedan debiendo $50). Acreedor borra el pago que
registró DEUDOR (el de $200): vuelven a deberle $250. Los dos funcionaron
con 200, y un id de otro grupo (probado con un segundo par de cuentas) dio
404 en los dos verbos, sin tocar los datos de ese otro grupo.

Verificado: 172 tests en el backend (167 + 5 nuevos en
`LiquidacionServicioTest`, incluido el caso "la puede corregir CUALQUIERA
de los dos"), todos en verde; `tsc --noEmit --noUnusedLocals` y `expo
export --platform ios` en mobile, limpios. Sección nueva en
`scripts/smoke-test.ps1` (15), con los números verificados a mano contra
Mongo real antes de escribirla. Sin teléfono esta vuelta.

### "Mi Plata": el saldo personal, y el rediseno del resumen (v1.1, seccion 2.3b)

Viole mando dos audios de WhatsApp usando la app de verdad, no en la
entrevista: **"algo muy importante para mi es saber cuanta plata me
queda"**, y pidio que la pantalla principal muestre a la vez cuanto gasto,
cuanto le queda, el gasto hormiga y la nutria -- aclarando que la nutria le
gusta tal cual esta ("me reta y eso esta buenisimo"). De paso propuso ganar
espacio: un boton flotante en vez del ancho de "Cargar un gasto", y un menu
de tres rayitas para las filas de navegacion.

**Esto contesta 2.3b**, que quedaba pendiente desde la sesion de la
vaquita: "Franco le pregunto si prefiere que la app solo sume sus gastos, o
que tenga un balance que se va descontando -- la respuesta nunca quedo
registrada". La respuesta es la segunda opcion, con una vuelta de rosca:
Franco senalo que un solo valor editable no alcanza, porque tiene VARIOS
ingresos a lo largo del tiempo (le pagan, le regalan plata, vende algo,
genera rendimientos) y necesita poder cargar todos, no resetear un numero.

**Cuatro decisiones, discutidas antes de tocar codigo (regla 3):**

1. **El gasto hormiga deja de ser el unico numero gigante.** Es un cambio
   consciente contra "el numero grande es el hormiga, no se negocia" (la
   frase textual del mockup de Lovable, en la seccion de diseno mas
   arriba): esa regla salio de una entrevista unica, y esto sale de meses
   de uso real, que en este proyecto ya peso mas antes -- la descripcion
   volviendose opcional es la misma clase de correccion. El hormiga NO se
   achica ni se saca: sigue con su nutria, en ambar, con la comparacion
   contra el mes pasado. Solo deja de ser el UNICO numero: ahora convive
   arriba con dos tiles nuevos, "Gastaste este mes" y "Mi Plata".
2. **"Mi Plata" es un ledger, no un campo que se pisa** -- exactamente el
   patron de `Pozo`/`Aporte`, pero para una sola persona: una lista de
   `Ingreso` (monto + fecha) embebida en `Usuario`, `restante = ingresado -
   gastado` calculado al vuelo, sin fechas ni corte de mes. Un ingreso mal
   cargado se corrige con un monto NEGATIVO -- mismo mecanismo que un
   aporte a la vaquita -- y cero se rechaza, tampoco es ingreso ni
   correccion.
   - **Deliberadamente AFUERA**: cuentas separadas por medio de pago,
     categorias de ingreso, graficos, tendencias de ahorro. Es lo que
     describe una app de finanzas personales completa, y contradice "todo
     es plata" (respuesta 11 de la entrevista) ademas de gastos/metas
     fuera de alcance del MVP. Se construyo el minimo que resuelve lo que
     Viole pidio, con un patron que ya existia y ya estaba probado.
   - **Solo resta gastos PERSONAL**, nunca la parte de un COMPARTIDO: eso
     ya lo trackea `saldo`/`Liquidacion` aparte, y mezclarlos seria doble
     contabilidad de la misma plata.
   - **No se llama "Saldo"**: ya lo usa la seccion de pareja. "Mi Plata" es
     copy de la UI unicamente -- el nombre tecnico es `BalancePersonal`
     (servicio) y `/balance-personal` (ruta), mismo criterio que "Saldar
     cuentas" (UI) vs `Liquidacion`/`/saldo/...` (codigo).
3. **El boton "Cargar un gasto" NO cambia.** Sigue ancho, terracota, abajo.
   No hay un solo precedente de boton flotante en toda la base de codigo, y
   es la interaccion mas probada de la app -- literalmente la que Viole
   eligio al evaluar los mockups ("boton de accion ancho abajo, se ve
   disenado"). El espacio se gano reorganizando las tarjetas de arriba, no
   achicando el CTA principal.
4. **Las tres filas de navegacion si se agrupan en un menu** ("ver los
   gastos del mes", "gastos compartidos", "la vaquita del viaje"). Es un
   patron nuevo -- no existia ningun drawer/menu en la app -- pero es la
   decision de Franco, pese a la advertencia de que oculta a "Gastos
   compartidos", uno de los dos pilares del producto.

**El modelo**: `Usuario` gano `List<Ingreso> ingresos` (embebido, sin campo
`usuario` a diferencia de `Aporte` -- ya esta adentro del dueno, no hace
falta decir de quien es). Sin `@Version` en `Usuario`, el `$push` atomico de
`UsuarioConsultasImpl.agregarIngreso` no necesita el `.inc` manual que si
hace falta en `PozoConsultasImpl.agregarAporte`. `GastoConsultas.
totalPersonalDe` es el lado de los debitos: mismo `sumar()` que ya usan
`saldoDe`/`sumarDelPozo`, sin `$cond` porque un PERSONAL siempre lo paga
entero quien lo carga.

**`BalancePersonalServicio` es propio**, no una lectura mas de
`ResumenServicio` -- mismo criterio que separo `PozoServicio` de
`GastoServicio` y `LiquidacionServicio` de `ResumenServicio`.

**En mobile**: `app/mi-plata.tsx` (mismo espiritu que `vaquita.tsx`: numero
grande, agregar/corregir con signo, historial), pero **sin nutria** -- no
hay un animo real para esta pantalla, y ponerle una decorativa vaciaria de
sentido el personaje, igual que ya se decidio para `borrar-cuenta.tsx`.
Color: nunca ambar (es del hormiga) ni teal (es de lo compartido) -- `hoja`
si alcanza, `terracotaProfunda` si no, mismo tono que usa el resto de la app
para "mira esto".

**El menu se hizo con el `Modal` nativo de React Native**, sin sumar
ninguna libreria de navegacion: alcanza para tres filas de texto. Un
`Pressable` de fondo semitransparente cierra al tocar afuera; tocar una fila
cierra el menu y navega en el mismo gesto.

**`resumen.tsx` ahora pide dos hooks en paralelo** (`useResumen` y el nuevo
`useBalance`), cada uno con su propio `sincronizar()` -- mismo patron ya
establecido entre `useSaldo` y `useResumen`, no una coordinacion nueva. Sin
esto, "Mi Plata" podria mostrar un numero viejo justo al lado de un resumen
ya sincronizado, que es la misma clase de inconsistencia que el bug de
`saldo.tsx` de mas arriba.

Verificado: 157 tests en el backend (152 + 5 nuevos de
`BalancePersonalServicioTest`), todos en verde salvo `contextLoads`;
`tsc --noEmit --noUnusedLocals` y `expo export` en mobile; y una seccion
nueva en `scripts/smoke-test.ps1` (16), sin correr todavia contra Mongo
real. **Se probo en el telefono en la sesion siguiente**, y de ahi salieron
tres correcciones -- ver "Corrigiendo Mi Plata con el uso real" mas abajo.

### Corrigiendo "Mi Plata" con el uso real: CRUD de Ingreso, el cartel en rojo, y el menu unico (v1.1, seccion 2.3c)

Primera vez que "Mi Plata" y el resumen rediseñado se probaron en un telefono
de verdad (regla 1: cerrar con algo probado, no solo compilado). De ese uso
salieron tres correcciones, discutidas con Franco antes de tocar codigo
(regla 3) y no decididas en silencio.

**1. `Ingreso` ahora se edita y se borra de verdad, tocando la fila.**

Hasta ahora, `Ingreso` (igual que `Aporte` de la vaquita y `Liquidacion`)
era un ledger inmutable: corregir un error de carga era anotar el asiento
contrario, con un monto negativo. Es el mismo patron en los tres, documentado
asi en los tres modelos.

Probandolo en el telefono, ese mecanismo se sintio como vueltas de mas para
algo tan simple como un error de tipeo. La primera propuesta fue mejorar el
boton de correccion; Franco pidio ir mas lejos: tocar la fila del historial
tiene que abrir un menu con **Editar** y **Borrar**, mismo espiritu que el
menu contextual de WhatsApp sobre un mensaje (pero disparado con un toque
simple, no con mantener presionado).

Eso **rompe a proposito la consistencia con `Aporte` y `Liquidacion`**, que
siguen siendo ledgers inmutables -- no se tocaron en esta sesion, y la
inconsistencia resultante es consciente, no un olvido. La diferencia de
fondo, que vale para una entrevista: en un aporte a la vaquita o una
liquidacion importa el RASTRO de los dos movimientos (quien aporto, quien le
pago a quien), porque son hechos entre dos personas. Un ingreso personal mal
tipeado no tiene ese valor historico -- es un dato a corregir, no un hecho
contable a enmendar.

Lo que costo tecnicamente, porque `Ingreso` era un `record` embebido sin
identidad (`BigDecimal monto, LocalDate fecha`):

- **Gano un campo `id`** (`UUID.randomUUID()`, generado en el servicio al
  crear). Sin el, no habia forma de direccionar CUAL ingreso tocar.
- **`UsuarioConsultasImpl.editarIngreso`** usa el operador posicional `$` a
  secas (`ingresos.$.monto`), con el filtro `_id + ingresos.id` en la MISMA
  query: si el id no existe, la query entera no matchea nada, y
  `modifiedCount` queda en 0 de una. A diferencia del `arrayFilters` que usa
  el borrado de cuenta para los aportes, aca alcanza el `$` simple porque el
  id ya es unico -- no hay el problema de "el primero que matchea" que
  `arrayFilters` existe para resolver.
- **`borrarIngreso`** usa `$pull` con una query por `id` sobre el array.
- Los dos devuelven `boolean` (matcheo o no), y el servicio lo traduce a
  `RecursoNoEncontradoException` (404) si no -- mismo patron que
  `GastoServicio.buscarVisible` con un gasto ajeno o inexistente.
- **`POST /balance-personal/ingresos` dejo de admitir negativo.** Si ya existe
  edicion de verdad, mantener el asiento-en-contrario como alternativa es una
  segunda forma de hacer lo mismo, y una innecesaria: `RegistrarIngresoRequest.monto`
  paso a `@Positive`, y el chequeo manual de "cero rechazado" que vivia en el
  servicio se borro -- Bean Validation ya cubre cero Y negativo en un solo
  lugar. El test que probaba el negativo-como-correccion se borro con el,
  porque el premiso que probaba dejo de ser cierto.

**2. El cartel en rojo: fondo tintado, no solo texto rojo.**

"Te pasaste por $X" en texto `terracotaProfunda` no se notaba lo suficiente
de un vistazo, y ademas suena a reto -- lo que choca con el principio que ya
regia la nutria ("una app que hace sentir culpable se desinstala").

Se evaluaron tres alternativas (regla 3): mejorar el cartel actual, mostrar
"ingresaste"/"gastaste" como dos numeros iguales, o una barra de progreso
estilo Mint/YNAB. La barra se descarto explicitamente: una barra que "se
llena" se lee como un tope o presupuesto, y los presupuestos estan
descartados a proposito en este proyecto por los ingresos irregulares de
Viole (respuesta 14 de la entrevista). Ganó la primera, la mas barata y la
que no arriesgaba reintroducir esa lectura.

Cambio: fondo `terracotaSuave` (color nuevo, agregado a `colores.ts` con la
misma formula oklch que ya usan `hormigaSuave`/`rioSuave` -- alta luminosidad,
poca saturacion, el matiz de `terracotaProfunda`) en vez de solo texto en
rojo, "Te pasaste por" paso a "Te falta" (mas corto, sin acusar), y se sumo
una linea nueva solo cuando esta en rojo invitando a la accion: "Cargá un
ingreso para ponerte al día". Mismo tratamiento en el tile de "Mi Plata" en
`resumen.tsx`, que muestra el mismo dato.

**3. El menu unico, mirando como lo resuelve Instagram.**

Antes habia DOS botones en el encabezado del resumen: el icono de menu
(seccion 2.3b, las tres filas de navegacion) y la pastilla de "Ajustes"
(aparte, pantalla propia). Franco penso que no cerraba a nivel de UX tener
dos entradas separadas, y pidio juntarlas bajo un unico icono hamburguesa,
con secciones -- mandando de referencia una captura de la pantalla de
ajustes de Instagram.

Se evaluo si "Tu cuenta" debia vivir inline en la pantalla nueva o seguir
siendo un link a `/ajustes` sin tocar. Gano inline, que es ademas lo que
pidio Franco literalmente ("que CONTENGA" las dos cosas): `app/menu.tsx` es
una pantalla nueva con dos secciones (`rotuloSeccion`, el mismo estilo de
encabezado gris que ya usa el resto de la app) -- "Navegacion" (las tres
filas de siempre) y "Tu cuenta" (las filas de `ajustes.tsx`, movidas tal
cual, con su mismo `useEffect` de `traerGrupo()` para `tienePareja`).
`app/ajustes.tsx` se borro: ya no tiene ningun lugar desde donde se la
pueda abrir.

El `Modal` chico que `resumen.tsx` usaba para las tres filas de navegacion
tambien se borro: ahora es una pantalla real, pusheada como cualquier otra.
La diferencia de UX es minima (ir y volver hace lo mismo), pero el codigo es
mas simple: una pantalla menos un estado de visibilidad, en vez de una
pantalla mas un modal superpuesto.

**Verificado:** 159 tests en el backend (161 - 2 tests que probaban el
negativo-como-correccion, que dejo de existir), todos en verde salvo
`contextLoads` (sin Mongo local en esta sandbox, mismo caso de siempre);
`tsc --noEmit --noUnusedLocals` y `expo export --platform ios` en mobile, los
dos limpios; `scripts/smoke-test.ps1` actualizado con los casos de editar,
borrar, e id inexistente (404), pero **sin correr todavia contra Mongo real**
-- esta sandbox no tiene Docker. Lo que eso no prueba, y que solo prueba un
telefono: que el `ActionSheetIOS` se vea y se sienta como el menu de
WhatsApp que pidio Franco, y que el `$pull`/`$set` posicional sean atomicos
de verdad contra una base real.

**Y probarlo en el teléfono (de verdad, esa misma noche) encontró tres cosas más.**

**El `ActionSheetIOS` funcionaba, pero editar se sentía lejos del dato.** Elegir
"Editar" abría el formulario de arriba, que para una lista larga puede quedar
scrolleado fuera de vista. Ahora la fila misma se convierte en un input (con
"Guardar"/"Cancelar" al lado) -- no hay que ir a ningún lado para corregir un
número. El formulario de arriba se oculta mientras tanto: mostrar los dos a la
vez, compartiendo el mismo estado `monto`, se vería como si escribieran en
espejo sin motivo.

**El número en rojo vuelve a mostrar el signo.** "Te falta $X" en rojo fue la
primera corrección de esta sección; probándolo de nuevo, Franco prefirió el
número con el signo puesto (`-$22.000,00`) en vez de la frase con el valor
absoluto -- más parecido a una cuenta, menos a una frase armada. El fondo
tintado y el aviso de "Cargá un ingreso para ponerte al día" se mantienen:
eso sí funcionaba.

**Un hallazgo real, no un bug: "gastaste" en Mi Plata no tiene corte de mes,
y eso sin avisarlo se lee como un error.** Con $10.000 de gastos en octubre,
Mi Plata decía "gastaste $23.000" -- numero correcto (suma TODOS los gastos
PERSONAL desde siempre, a propósito: ver "El modelo" más arriba, `GastoConsultas.
totalPersonalDe` es `saldoHistoricoDe` sin el recorte de fechas), pero sin
contexto parece roto al lado de "Gastaste este mes" en Resumen, que sí es solo
del mes. El arreglo no es de lógica -- la lógica ya hacía lo que Franco quería,
que de hecho pidió de nuevo sin darse cuenta ("que no se borre cada cambio de
mes") -- es de copy: ahora dice "Ingresaste $X en total · gastaste $Y en
total". Vale como lección: una decisión correcta y ya implementada puede
leerse como un bug si el número no dice de dónde sale.

**El menú: "Tu cuenta" se reordenó** (Política de privacidad, Salir del
grupo/Sumarse a un grupo, Cerrar sesión al final -- la única fila sin flecha,
porque no navega, actúa) **y "Salir del grupo" dejó de personalizarse** con
el nombre de la otra persona ("Compartís gastos con Violeta" → "Salir del
grupo", en línea con "Sumarse a un grupo"). `otroNombre` quedó sin ningún
uso y se borró del componente, junto con el `find` que lo calculaba.

**Y un bug de datos de verdad, que vale como anécdota de "Schema: no hay. Es
MongoDB."** `Ingreso` sumó el campo `id` esta sesión, pero los ingresos que
Franco había cargado en sesiones anteriores (18, en su propia cuenta) seguían
en Mongo sin ese campo -- Mongo no migra nada solo. React se quejó con
"Encountered two children with the same key, `null`", en un mensaje de error
que el log de Metro mostraba vacío (encontrado recién mirando la pantalla
roja del teléfono directamente, no la terminal). Diagnosticado con una query
de solo lectura en Atlas Data Explorer (`{ "ingresos.id": null }`), que
confirmó que la cuenta de Viole no tenía ni un ingreso cargado -- el riesgo
era enteramente de datos de prueba propios, nada de ella. Resuelto con un
`$pull` sobre esa misma cuenta, aplicado a mano desde el Data Explorer (sin
`mongosh`, que Franco nunca usó). Las filas viejas sin `id` no se podían ni
editar ni borrar desde la app -- dependían del mismo dato que les faltaba --
así que sacarlas no perdía ninguna funcionalidad, solo números de prueba.

Verificado de nuevo: `tsc --noEmit --noUnusedLocals` y `expo export
--platform ios` limpios. Sin cambios de backend en esta vuelta, así que no
hizo falta volver a correr `mvnw test`.

### La vaquita: quién gastó cuánto, informativo

Pregunta de Franco pensando en el viaje, no de la entrevista: *"si aportan los
dos lo mismo y uno gasta más que el otro, hoy no se sabe quién gastó más."*
Es una pregunta DISTINTA de la que `docs/vaquita.md` ya dejaba anotada como
pendiente ("el número del desbalance tampoco se calcula"): esa es sobre
APORTES y sí mueve el invariante (aportar distinto genera deuda); esta es
sobre GASTOS, y **no mueve nada** -- la plata del pozo ya es de los dos desde
que entró, así que no importa quién pagó cada cosa.

**Discutido antes de programar (regla 3) con una pregunta concreta: informativo
como `porPersona`, o una deuda nueva.** Ganó lo informativo: una deuda por
diferencia de GASTO rompería el invariante central de la vaquita ("sacar
plata del pozo no genera deuda entre ellos, porque la plata ya se repartió al
aportar"). Si Franco gasta $300.000 del pozo y Viole $0, eso no significa que
Viole le deba nada -- los dos ya pusieron lo mismo al aportar. Detalle
completo, con el ejemplo numérico, en `docs/vaquita.md`, sección 10.

**La implementación no agregó ningún campo nuevo**, porque el dato ya
estaba: todo `Gasto` -- del pozo o no -- guarda `pagadoPor`. Solo hacía falta
agruparlo. `GastoConsultas.gastadoPorPersonaDelPozo(pozoId)` es el mismo
pipeline `$match` + `$group` que ya usaba `sumarDelPozo`, agrupando por
`pagadoPor.usuarioId` en vez de por `_id: null` -- una fila por persona en
vez de una sola con el total de todos. Se factorizó `aDecimal()` de `sumar()`
en `GastoConsultasImpl` porque las dos consultas necesitan la misma
conversión de `Decimal128`.

`PozoServicio.gastadoPorPersona(pozo)` combina esas filas con los integrantes
del grupo con el mismo armado que `totalesPorPersona` ya usa para los aportes:
los dos SIEMPRE (incluido el que gastó cero), más quien gastó del pozo y ya
no está en el grupo (mismo caso "Cuenta eliminada"). La diferencia con
`totalesPorPersona` es de dónde sale el "quién": ahí es un campo embebido en
`Pozo` (`pozo.getAportes()`); acá los gastos viven en su propia colección, así
que el "quién" sale directo de la consulta ya agrupada.

Nuevo campo `PozoRespuesta.gastadoPorPersona`, y en mobile un bloque "Quién
gastó cuánto" al lado de "Quién aportó cuánto" en `vaquita.tsx` y en
`viaje/[id].tsx` (el viaje cerrado) -- mismo componente de fila, datos ya
resueltos por el backend.

Verificado: 162 tests en el backend (159 + 3 nuevos en `PozoServicioTest`,
incluido uno que prueba explícitamente que `gastado` y `restante` no se
mueven con esto), todos en verde salvo `contextLoads`; `tsc --noEmit
--noUnusedLocals` y `expo export --platform ios` limpios; `scripts/smoke-test.ps1`
con los chequeos nuevos de la sección 10.

**Y esto SÍ se corrió contra Mongo real, desde la nube.** Corrección a una
asunción repetida en sesiones anteriores ("esta sandbox no tiene Docker"):
Docker está instalado, solo que el daemon no arranca solo. `sudo dockerd &`
lo levanta, y de ahí `docker compose up -d` trae el Mongo del proyecto sin
tocar nada más. Con eso, `contextLoads` corrió de verdad (antes el único
test que lo necesita quedaba como el único error esperado, sesión tras
sesión) y los 161 tests dieron verde. Lo que no hay en esta sandbox es
PowerShell (`pwsh`), así que `smoke-test.ps1` tal cual no corre -- pero la
consulta nueva se probó igual, a mano: levantando el backend contra ese
Mongo y pegándole por `curl` (dos usuarios de prueba, un pozo, un aporte de
cada uno, un gasto del pozo pagado por cada uno). `gastadoPorPersona` separó
$50.000 de uno y $30.000 del otro correctamente, y `porPersona` (los
aportes) no se movió -- confirma que el pipeline `$match`+`$group` de
`gastadoPorPersonaDelPozo` agrupa por `pagadoPor.usuarioId` de verdad contra
una base real, no solo en la forma que el mock le daba por programado.
Vale la corrección para la próxima sesión en la nube: Docker SÍ está, y
cambia bastante qué se puede probar sin la PC.

### La vaquita: cuánto le queda a cada uno, al cerrar

Pedido de Franco, charlando sobre el cierre del viaje: quiere que al cerrar
la vaquita aparezca cuánto le sobró a cada uno (aporte menos lo que gastó),
para poder sacar del pozo lo que le corresponde a cada uno. A diferencia de
`porPersona` y `gastadoPorPersona` (sección de arriba), que son puramente
informativos, **este número decide plata real**.

**Dos rondas de discusión antes de programar (regla 3), y las dos valieron
la pena:**

1. La fórmula (`aporte - gastado por esa persona`) puede dar **negativo**: si
   alguien paga la mayoría de las cosas del viaje con la tarjeta del pozo, su
   número queda negativo aunque la plata se haya gastado en los dos por
   igual -- ata el sobrante a QUIÉN PAGÓ, no a quién se benefició. Un
   negativo no se puede "sacar" literalmente del pozo: es una deuda.
2. La primera idea de Franco para ese caso fue **bloquear la carga de un
   gasto si a esa persona ya no le queda aporte**. Se descartó: choca directo
   con el principio más fuerte de todo el proyecto, "bloquear una carga
   parada en el mostrador es el pecado capital de esta app" (la razón por la
   que existe: Viole abandonó un Excel anterior por exactamente esa
   fricción). Y el pozo entero YA hace esto mismo a propósito -- `restante`
   puede dar negativo y queda en rojo, probado en el smoke test -- así que
   dejar que el número por persona también dé negativo no es una excepción
   nueva, es la misma regla un nivel más abajo.

**Se eligió: no bloquear nada, mostrar el sobrante tal cual, con signo.** Si
a alguien le da negativo, le tiene que devolver esa diferencia al pozo (o a
la otra persona) antes de repartir el resto -- arreglan la plata entre
ustedes, fuera de la app, como ya estaba pensado para el sobrante total
(`docs/vaquita.md`, sección 9: "vuelven, ven que sobraron $120.000 y se lo
transfieren"). El número por persona solo les dice CÓMO dividir ese
transfer, no lo ejecuta.

**Cero consultas nuevas**: `PozoServicio.sobrantePorPersona` resta los dos
desgloses que ya existían (`porPersona` - `gastadoPorPersona`), uniendo los
dos conjuntos de ids por si alguien aportó sin gastar nunca del pozo, o gastó
sin haber aportado nada. Nuevo campo `PozoRespuesta.sobrantePorPersona`.

En mobile, un tercer bloque -- "Cuánto le queda a cada uno" -- pero **solo en
`viaje/[id].tsx`** (el viaje cerrado, no `vaquita.tsx`): mientras el viaje
sigue abierto, quién pagó qué todavía puede cambiar. Con el signo puesto, sin
`Math.abs()` -- mismo criterio que "Mi Plata" en negativo.

Detalle completo, con el ejemplo numérico trabajado paso a paso, en
`docs/vaquita.md`, sección 11.

Verificado: 163 tests en el backend (161 + 2 nuevos, incluido el caso
negativo con el ejemplo exacto de la discusión: aportan $400.000 cada uno,
uno paga $600.000 del pozo y el otro $100.000 → sobrante -$200.000 / 
$300.000), todos en verde salvo `contextLoads` (que esta vez SÍ corrió, con
Mongo real); `tsc --noEmit --noUnusedLocals` y `expo export --platform ios`
limpios; y el mismo caso corrido a mano contra Mongo real con `curl`, con el
pozo ya CERRADO, dando el resultado esperado.

### La vaquita: el aporte se edita y se borra de verdad

Pedido de Franco: que un aporte se corrija tocando la fila, igual que ya
funciona un ingreso de "Mi Plata" (sección 2.3c). Al agregarlo, se borró el
botón "Me equivoqué: sacar esta plata del pozo", que era la corrección por
signo. **Rompe a propósito la consistencia con `Liquidacion`**, que en su
momento quedó como el único ledger sin ningún mecanismo de corrección: la
razón es la misma que ya separaba `Ingreso` de `Aporte`/`Liquidacion` en la
2.3c -- a un `Aporte` corregirlo no pierde nada, a una `Liquidacion` la
DIRECCIÓN ("quién le pagó a quién") es el hecho que vale la pena preservar.
(`Liquidacion` también terminó ganando edición y borrado poco después --
ver más abajo -- pero sin poder tocar esa dirección, que sigue siendo lo
que la distingue.)
Detalle completo en `docs/vaquita.md`, sección 12.

`Aporte` ganó un campo `id` (no lo tenía: nada permitía decir "este aporte,
no el otro"). `AporteRequest.monto` pasó a `@Positive` -- ya no hay
corrección por signo, así que un aporte siempre es "puse esta plata".

**Dos bugs reales en `PozoConsultasImpl`, los dos encontrados contra Mongo
real y ninguno con un mock:**

1. `editarAporte`/`borrarAporte` filtraban con dos condiciones SUELTAS
   sobre el array (`"aportes.id"` y `"aportes.usuario.usuarioId"`), que en
   Mongo NO exigen que las cumpla el mismo elemento -- cada una matchea
   contra CUALQUIER elemento por separado. Con las dos personas teniendo
   aporte propio en el mismo pozo, pedir tocar el aporte de una pasando su
   id pero el usuarioId de la OTRA podía matchear igual.
2. El `$inc` de `version` no es condicional al `$pull`: con el chequeo de
   dueño solo adentro del filtro del pull, borrar el aporte ajeno hacía
   subir `version` igual (el documento matcheaba por `_id`+`grupo_id`) y
   `modifiedCount` daba mayor a cero sin haber borrado nada -- HTTP 200 en
   vez de 404. Este fue el que realmente se vio fallar en una prueba viva
   antes de arreglarlo, no uno encontrado solo leyendo código.

Los dos se resuelven igual: el filtro de ARRIBA pasa a usar `$elemMatch`
(id + usuario.usuarioId del MISMO elemento), así que ni el pull ni el inc
corren si el aporte no existe o no es de quien lo pide. Verificado con el
caso que de verdad expone el bug 1: dos usuarios con aporte propio cada
uno, cruzando los intentos de editar/borrar el aporte del otro (404 los
cuatro) y el propio (200 los dos).

En mobile, `app/vaquita.tsx` copia el patrón de `mi-plata.tsx`: tocar la
fila abre un `ActionSheetIOS`, Editar convierte la fila en un input inline,
y el formulario de arriba se oculta mientras tanto.

Verificado: 167 tests en el backend, todos en verde; `tsc --noEmit
--noUnusedLocals` y `expo export --platform ios` en mobile, limpios.
Probado a mano con `curl` contra Mongo real -- de ahí salieron los dos
bugs. Se sumó la sección correspondiente a `scripts/smoke-test.ps1`, sin
correrla todavía (sin PowerShell en esta sandbox); sin teléfono esta
vuelta tampoco.

### Un atajo para cargar un gasto desde adentro de la vaquita

Pregunta de Franco, pensando en developer senior: ¿convenía dejar cargar un
gasto directamente desde `vaquita.tsx`, en vez de obligar a ir a la
pantalla principal y elegir el chip Vaquita ahí? Sí, pero como ATAJO de
navegación, no como un formulario nuevo -- `FormularioDeGasto` sigue
siendo uno solo, por el mismo motivo de siempre (la inversión del
porcentaje vive en un solo lugar a propósito, sección 6.9).

**El hueco real que esto tapa**: el default automático a Vaquita se ata a
`vigente` (hoy cae entre las fechas del viaje), a propósito -- ver "Tres
huecos que encontraron las auditorías de la sesión 6.10". Pero eso deja
afuera comprar algo DEL viaje antes de que arranque o después de que
termine (pasajes, el hotel pagado por adelantado): justo cuando es más
probable que alguien esté mirando la pantalla de la vaquita para planear,
y el formulario principal no lo va a defaultear solo.

**Cómo quedó**: un botón chico con borde ("+ Agregar gasto") arriba de
todo en `vaquita.tsx`, que navega a `/gasto/nuevo?destino=VAQUITA`.
`FormularioDeGasto` ganó una prop opcional `destinoSugerido` que solo
cambia el PUNTO DE PARTIDA del chip -- la persona sigue pudiendo tocar
cualquiera de los tres. **Chico y con borde, no el `Boton` ancho de
terracota**: esta pantalla ya tiene uno ("Aportar"), y `Boton.tsx` es
explícito -- un solo botón terracota por pantalla, o ninguno es el
principal.

**De paso, un ref que había quedado muerto.** `eligioAMano` (un
`useRef` en `FormularioDeGasto`) existía solo para que el default
automático a Vaquita -- el que la sesión 6.10 sacó por el bug del regalo
sorpresa -- no pisara una elección ya hecha a mano. Al sacar ese default,
nadie volvió a LEER el ref: quedó escribiéndose en cada tap sin que nada
lo mirara. Se encontró tocando esta misma zona del código para
`destinoSugerido`, y se borró -- no por prolijidad sola, sino porque
dejarlo invitaba a alguien a asumir que todavía protegía algo.

Verificado: `tsc --noEmit --noUnusedLocals` y `expo export --platform
ios`, limpios (no hay cambios de backend: el shortcut es pura navegación).
Falta probarlo en el teléfono: que el botón se vea bien arriba de la
tarjeta, y que llegar por ahí deje el chip Vaquita puesto de una.

### El animo de la nutria: tendencia, tres estados
`CONTENTA` / `TRANQUILA` / `PREOCUPADA`, calculado en el backend.

Compara el gasto hormiga del **tramo transcurrido** del mes contra el **mismo
tramo** del mes anterior (6 dias contra 6 dias, no 6 contra 31). Baja de 10% o mas
-> CONTENTA; sube 10% o mas -> PREOCUPADA; en el medio -> TRANQUILA. Cero hormiga
-> CONTENTA. Sin datos del mes anterior -> TRANQUILA: la nutria no juzga el primer
mes de uso. **Y con menos de 5 dias transcurridos tampoco**: el 1 de cada mes la
comparacion es un dia contra un dia, donde la banda de +-10% no protege de nada
-- si el 1 del mes pasado no hubo hormiga, un solo cafe dejaba a la nutria
PREOCUPADA. El chequeo va despues del de cero hormiga, porque "no gastaste nada
evitable" es verdad el dia 1 igual que el dia 20: no es una comparacion.

Es una medida relativa porque los ingresos son irregulares, y es contra el pasado
y no contra una meta porque el objetivo declarado de la usuaria es **bajar**, no
estar debajo de una linea. La banda de +-10% evita que cambie de humor por ruido.

La logica vive en `CalculadorDeAnimo` y `Periodo`, dos clases puras sin Spring ni
base, para que se puedan testear barato.

### La cola offline, y por que obligo a una clave de idempotencia
El alta de gasto **ya no espera a la red**: el gasto se escribe en un archivo del
telefono (`expo-file-system`, en `Paths.document`) y se manda despues. Guardar
paso a ser una operacion de disco, instantanea, que anda igual con una barra de
senial o con ninguna. Es lo que hacia falta para sostener el requisito duro del
producto en Bariloche.

Se encola **siempre**, incluso con red perfecta. Si primero intentaramos mandar y
solo encolaramos al fallar, el gasto se perderia igual cuando iOS mata la app en
medio de la request -- que es lo que hace apenas abris la camara.

**Y eso obligo a una clave de idempotencia, que es la parte que importa.** Con
mala senial pasa que el POST llega, el servidor escribe el gasto, y la respuesta
se pierde de vuelta. El telefono no puede distinguir eso de "no llego", asi que
reintenta: sin clave, ese reintento crea un segundo gasto. **Un gasto duplicado
es peor que uno perdido**, porque no se ve como un error sino como un total del
mes equivocado, en silencio.

`Gasto.clienteId` lo genera el telefono, y un **indice unico parcial**
`{grupo_id, cliente_id}` lo garantiza desde la base. El servicio intenta escribir
de una y atrapa `DuplicateKeyException` para devolver el gasto que ya existia: el
chequeo previo tendria una ventana de carrera entre el SELECT y el INSERT. Es el
mismo patron que el pozo abierto unico.

Es opcional: un cliente que no la manda (el atajo de iOS, curl) escribe normal y
se queda sin la garantia.

Tres reglas del sincronizador que salieron de pensar el viaje:
- **Se frena en el primer fallo de red.** Si el primero no entro por falta de
  senial, los siguientes tampoco: seguir es sumar un timeout por gasto.
- **Un 4xx no se reintenta**, porque va a dar el mismo error para siempre y
  taparia a los que si pueden entrar. Pero **no se borra**: se muestra y decide
  la persona. Descartar un gasto por decision de la app seria la perdida de datos
  que la cola viene a evitar.
- **La cola no es invisible.** `AvisoDeCola` muestra cuantos hay pendientes. Una
  app que dice "guardado" y no muestra el gasto hace que la persona lo cargue de
  nuevo, o sea que lo duplique.
- **`sincronizar()` nunca rechaza.** Las pantallas lo llaman antes de leer; si un
  fallo de escritura se propagara, el resumen mostraria un error con el servidor
  perfecto. Un problema en la cola no deja la app inutilizable.

**Y toda mutacion de la cola pasa por un mutex**, porque sin eso pierde gastos.
Cada una es leer-modificar-escribir, y entre el `await leerCola()` y el
`escribirCola()` JavaScript cede el control: `sincronizar()` llama a
`quitarDeLaCola(A)`, que lee `[A]` y cede; justo ahi alguien guarda el gasto B y
se escribe `[A, B]`; `quitarDeLaCola` retoma con su copia vieja y escribe `[]`.
**B desaparecio.** Y no es rebuscado: sincronizar corre en cada foco de pantalla,
o sea justo al volver del alta.

Es la misma carrera que en el backend se evita con `$push` atomico. Aca alcanza
con encadenar las operaciones, porque JavaScript es de un solo hilo.

El timeout de las lecturas es de 75s, generoso a proposito porque el arranque en
frio de Render es de 40-60s. Se puede pagar esa espera **solo porque el alta ya
no la sufre**.

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

### Un 404 salia como 401, y la app cerraba sesion sola
Bug real, encontrado probando en el telefono. Vale como historia porque la causa
no esta en ningun lado obvio.

Cuando una request **autenticada** pega contra una ruta que no existe, Spring MVC
responde 404 y Boot lo reenvia internamente a `/error`. Ese reenvio **vuelve a
pasar por la cadena de filtros de Spring Security**, pero `FiltroJwt` NO se
ejecuta la segunda vez: hereda de `OncePerRequestFilter`, que trae
`shouldNotFilterErrorDispatch()` en `true` por defecto. Sin nadie autenticado en
ese segundo paso, `/error` cae en `anyRequest().authenticated()` y el 404 sale
por la puerta convertido en **401**.

El sintoma en la app fue peor que el bug: el cliente llamo a `GET /grupo`, que el
backend deployado todavia no tenia, recibio "Falta el token, o no es valido", y
**cerro la sesion sola** aunque el token estuviera perfecto.

Se arregla con `.requestMatchers("/error").permitAll()`. No abre nada: `/error`
no devuelve datos, es adonde Boot reenvia para renderizar un error ya decidido.
Cubierto por el smoke test: *"una ruta inexistente da 404 y no 401, aun con token
valido"*.

### La sesion se cierra sola cuando el backend rechaza el token
Un JWT de esta app dura 30 dias y vive en el Keychain, asi que al abrir la app
hay token guardado mucho despues de que dejo de servir. Antes el cliente asumia
que token guardado = token valido: entraba al resumen, cada pantalla se comia un
401 y no habia forma de salir. Un estado zombi.

Ahora `cliente.ts` avisa cuando un 401 llega en una request **con** token, y el
provider de sesion cierra sesion y navega al login. La condicion importa: el 401
del login es "esa contrasena esta mal" y ahi no hay sesion que cerrar.

La alternativa era validar al arrancar con un `GET /auth/yo`, que no existe.
Reaccionar al 401 no necesita endpoint nuevo y ademas cubre el token que se
invalida con la app ya abierta.

**Y eso choco de frente con la cola offline al juntar las dos ramas.** El cierre
automatico llamaba al mismo `salir()` que el boton, y `salir()` vacia la cola
para que los gastos de uno no se manden con el token del otro. Combinado: se
vence el token, la app sincroniza al abrir, recibe 401, cierra sesion y **borra
los gastos que todavia no habia mandado** -- justo lo que la cola existe para
evitar, y justo cuando mas probable es que haya (un token se vence despues de 30
dias sin usarse). Por eso `salir()` ahora recibe un `MotivoDeSalida`: la cola se
borra cuando cambia la PERSONA, no cuando se muere el TOKEN.

### Tres huecos que encontraron las auditorias de la sesion 6.10
- **Una vaquita cerrada quedaba inalcanzable.** Sus gastos no salen en la lista
  del mes (`sinPozo()`) y `/pozos/activo` deja de devolverla al cerrarse, asi que
  el permiso de corregirlos -- agregado a proposito en la 6.9 -- no tenia ninguna
  pantalla desde la cual ejercerse. Ahora hay `GET /pozos` y `app/viaje/[id].tsx`.
- **Un aporte equivocado no se podia deshacer.** Los aportes son inmutables a
  proposito y `docs/vaquita.md` decia "si estuvo mal, se compensa con otro", pero
  la API no dejaba: el monto era `@Positive`. Ahora admite negativo, que es el
  asiento en contrario. Cero se rechaza: no es aporte ni correccion.
- **El default automatico a Vaquita se saco.** La pantalla que abre la vaquita no
  pide fechas, asi que `vigenteEl()` daba true siempre y cada alta abria en
  VAQUITA desde dos semanas antes del viaje -- y VAQUITA es COMPARTIDO, o sea
  visible para los dos. Un regalo sorpresa cargado rapido se publicaba solo.
  El default vuelve a PERSONAL: lo compartido se elige, nunca se asume.


### Los tests: dos capas, y que prueba cada una
**131 tests, y corren solos en cada push** (`.github/workflows/ci.yml`). Conviene
saber por que estan partidos en dos clases de cosas.

**Clases puras** (`Periodo`, `CalculadorDeAnimo`, `Pozo`, `PoliticaDeContrasenas`,
`ValidacionDeConfiguracion`): no tocan Spring ni la base, se construyen a mano y
corren en milisegundos.

**Servicios con mocks** (`GastoServicioTest`, `PozoServicioTest`,
`AutenticacionServicioTest`, `ResumenServicioTest`, `CuentaServicioTest`,
`RecuperacionServicioTest`): los repositorios son objetos
falsos de Mockito, programados con `when(...).thenReturn(...)`. Asi se ejercita
la LOGICA sin que exista una base. Cubren lo que mas duele si se rompe: el
centavo del reparto, que un PERSONAL ajeno de 404 y no 403, que editar sin
`pagadoPorId` **conserve** el pagador en vez de apropiarselo, que un reintento
con `clienteId` devuelva el mismo gasto, que un PERSONAL no pueda salir de la
vaquita, que el tercer integrante se rechace, y que el total del resumen sea MI
parte y no el total del grupo.

Dos decisiones de los tests de auth que vale la pena poder explicar:

- **El `PasswordEncoder` es un spy de un BCrypt de verdad, no un mock.**
  Mockeandolo, un servicio que comparara contrasenas en texto plano pasaria los
  tests igual -- justo lo que no puede pasar nunca. El spy da BCrypt real y
  ademas permite contar las llamadas.
- **El hash senuelo se verifica contando la llamada, no midiendo el tiempo.**
  La primera version hacia `assertThat(ms).isGreaterThan(30)` y era un flake
  esperando a pasar: BCrypt tarda distinto en cada maquina, asi que en un runner
  rapido el mismo codigo correcto fallaria. Y un test que falla sin que nadie
  haya roto nada ensucia justamente la senial que el CI viene a dar. Contar que
  `matches()` se llamo igual prueba el mecanismo; el costo en tiempo es
  consecuencia.

**Lo que un mock NO prueba**, y por eso el smoke test no sobra: que la consulta
de Mongo este bien escrita, que el indice parcial unico exista de verdad, que
`$push` sea atomico. Un mock programado para devolver `true` devuelve `true`
aunque la query este al reves. Las dos capas se complementan: aca las reglas,
alla que la base entienda lo que le pedimos.

Un detalle que salio de escribirlos: los mocks de `save()` devuelven el
documento **con id**, porque es lo que hace Mongo. Con id null, lo que se rompe
tres lineas despues no tiene nada que ver con la regla que se estaba probando.

**El CI corre las dos capas y ademas levanta una Mongo de verdad** como service
container, para que `contextLoads` -- el unico test que prueba que el grafo de
beans resuelva -- pueda correr. De paso se arreglo que `backend/mvnw` estaba
versionado sin permiso de ejecucion (`100644`): en Windows no se nota, pero en
cualquier runner Linux `./mvnw` da "Permission denied".

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
    modelo/       documentos de Mongo (@Document) y sus snapshots embebidos
    repositorio/  interfaces de Spring Data
    servicio/     logica de negocio, unico lugar con reglas
    controlador/  endpoints REST, finitos: reciben, delegan, devuelven
    dto/          records de entrada y salida. La API NUNCA expone entidades
    seguridad/    JWT, filtro, config de Spring Security y UsuarioActual
    error/        excepciones de dominio + @RestControllerAdvice
    config/       conversores de Mongo y el sembrador de categorias
    mail/         EnviadorDeMails: por la API de Gmail, o al log si no hay credenciales
/mobile       Expo. Por features, no por capas: ver docs/diseno.md
/web          React + Vite (despues del MVP)
.github/workflows/ci.yml tests, tipos y bundle en cada push
docker-compose.yml       MongoDB local
render.yaml              el servicio de Render, versionado y no en un panel
scripts/
  smoke-test.ps1         chequeos de la API contra el backend corriendo
  crear-cuenta-demo.ps1  la cuenta del revisor de Apple, con gastos de ejemplo
  capturas-app-store.ps1 capturas del telefono al tamano de App Store Connect
docs/
  entrevista-usuaria.md  fuente de verdad de las decisiones de producto
  diseno.md              colores, tipografias, nutrias y estructura de carpetas
  deploy.md              runbook del deploy (Render + Atlas)
  vaquita.md             el pozo del viaje: modelo, invariante y lo descartado
  atajo-ios.md           atajo de Atajos que le pega a POST /gastos con Back Tap
  app-store.md           todo lo de App Store Connect, y los pasos para mandar a review
  soporte.md             la pagina de soporte publica (URL de soporte de la ficha)
  mails.md               como configurar la API de Gmail para el codigo de reseteo
  proxima-sesion.md      lo que falta: cerrar la v1.0 y el alcance de la v1.1
  aprendizaje/           notas de Java y Spring para el autor

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
- [x] **6 — App Expo con el MVP completo.** Contra la API deployada, no
      localhost. Cinco pantallas: login, resumen con el animo de la nutria, alta
      de gasto, **lista de gastos del mes** (donde se ve la marca de hormiga
      gasto por gasto, o sea el producto) y **saldo** (la seccion de pareja, con
      las dos nutrias). El alta carga PERSONAL y COMPARTIDO, con reparto y con
      quien pago. Los iconos de categoria se dibujan con Lucide y no como texto.
      El token va en `expo-secure-store`, nunca en AsyncStorage.

      Se agrego `GET /grupo` en el backend, que era lo unico que le faltaba al
      cliente para armar un COMPARTIDO completo: el login dice quien sos vos y
      nada mas. El endpoint **no acepta un id** -- devuelve siempre el grupo de
      quien pregunta, que sale del token -- asi que por construccion no puede
      filtrar datos de otro grupo.

      **OJO CON UNA COSA, que es el punto mas facil de romper de la app:** el
      campo `porcentajePagador` es la parte de QUIEN PAGO, y los chips del alta
      preguntan por TU parte. Cuando pago la otra persona hay que invertir el
      numero (`100 - x`). Sin eso, el saldo sale al reves. Vive en
      `FormularioDeGasto`, en un solo lugar y a proposito.

      **Lo que esta verificado y lo que no.** El backend, entero: 25 tests y los
      64 chequeos del smoke test en verde. La app, NO en un telefono -- solo
      `tsc --noEmit` y `expo export`, que bundlea de verdad y prueba que todo el
      grafo de imports resuelve, pero no que algo se vea bien. **Contra la regla
      1 de este documento, esta sesion se cierra sin esa prueba.** El recorrido
      que ejercita lo nuevo: cargar un compartido con "pago la otra persona" y
      reparto 70/30, ver "tu parte" en la lista, y confirmar la direccion de la
      deuda en la pantalla de saldo.


      Quedo afuera a proposito: **el tab bar**, que segun `docs/diseno.md` nace
      con la pantalla de saldo. Cambia el layout del resumen (el boton de abajo
      pasa a convivir con la barra y el `insets.bottom` se cuenta dos veces), y
      eso hay que verlo en pantalla para ajustarlo. Saldo quedo como push, igual
      que la lista. Tambien quedaron afuera editar y borrar, y los filtros por
      categoria y pagador del listado (el endpoint ya los acepta).
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

      Preparado: el runbook en **`docs/deploy.md`** y **`render.yaml`** en la
      raiz, que deja la configuracion versionada en vez de en un panel. Falta
      ejecutarlo (crear las cuentas, deployar y verificar).
- [~] **6.7 — La vaquita del viaje.** Un pozo compartido al que los dos aportan
      y del que salen los gastos de un viaje (Bariloche: $800.000, $400.000 cada
      uno). Un documento `Pozo` con los aportes embebidos mas un `pozoId`
      nullable en `Gasto`; sacar del pozo no genera deuda entre ellos porque la
      plata ya se repartio al aportar. **Un aporte al pozo es la `Liquidacion`
      que el saldo mensual siempre necesito.** Los gastos del pozo NO cuentan
      para el animo de la nutria. Ver **`docs/vaquita.md`**, que incluye por que
      no se importan los movimientos de Mercado Pago.

      **El backend esta hecho**: cinco endpoints bajo `/pozos`, los aportes con
      `$push` atomico, un indice parcial unico que garantiza un solo pozo abierto
      por grupo, y el filtro `sinPozo()` en los tres agregados mensuales. Mas 13
      tests puros nuevos en `PozoTest` y 30 chequeos nuevos en el smoke test.

      **La pantalla en mobile tambien esta**: `app/vaquita.tsx` con los dos
      modos (el estado vacio ES el formulario para abrirla), y el tercer chip en
      el alta. El booleano `esCompartido` paso a ser un tipo `Destino` de tres
      valores; `VAQUITA` no existe en el backend, donde sigue siendo un
      COMPARTIDO con `pozoId`.

      Nada de esto se probo contra una base ni en un telefono. El backend se
      verifico compilando y con los tests puros; el mobile con `tsc --noEmit` y
      `expo export`, que bundlea de verdad. Lo que lo prueba en serio es
      `scripts/smoke-test.ps1`, que necesita la app corriendo.
- [~] **6.8 — La cola offline.** El alta de gasto ya no espera a la red: se
      escribe en un archivo del telefono y se manda despues. Obligo a agregar
      `Gasto.clienteId` con indice unico parcial, porque una cola que reintenta
      sin clave de idempotencia duplica gastos. Ver la seccion de arriba. Unica
      dependencia nueva: `expo-file-system`, en la version que manda
      `bundledNativeModules.json` del SDK 57.

      **Falta probarlo en un telefono de verdad**, que es lo unico que prueba una
      cola offline: poner el telefono en modo avion, cargar tres gastos, sacarlo
      de avion y ver que entran los tres una sola vez.
- [~] **6.9 — Editar y borrar desde la app.** En un viaje se carga parado y
      rapido, asi que se erra un monto o se olvida el toggle de hormiga. Un
      registro que no se puede corregir deja de ser confiable, y uno que no es
      confiable se abandona.

      Tocar una fila de la lista abre `app/gasto/[id].tsx`. **No hay swipe para
      borrar**: el borrado vive adentro de la edicion, detras de una
      confirmacion. Un swipe sobre una lista que se scrollea con el pulgar,
      parado y con una mano, es como se borra un gasto sin querer.

      El formulario se extrajo a `FormularioDeGasto` y lo comparten el alta y la
      edicion. **No se duplico** porque adentro vive la inversion del porcentaje
      cuando pago la otra persona, y tener esa cuenta escrita dos veces es como
      el saldo termina al reves en una de las dos pantallas.

      Tres cosas que salieron de hacerlo:
      - **`GET /gastos/{id}`**, que no existia. Sin el, la pantalla tendria que
        recibir el gasto por parametro de navegacion (texto) o traerse el mes
        entero para buscar uno (se rompe si el gasto es de otro mes).
      - **`GastoRespuesta.porcentajePagador`**, derivado como `deudaGenerada`.
        Preseleccionar el chip de reparto en el cliente exigiria dividir
        montoPagador por monto: aritmetica de plata en punto flotante, que es
        justo lo que el proyecto evita de punta a punta.
      - **Una vaquita cerrada ya no congela sus gastos.** Cerrar impide meter
        gastos nuevos, pero permite corregir los que ya tenia. Si no, un cero de
        mas visto al volver del viaje quedaria para siempre.

      El PUT ahora manda `version` siempre, asi que el pendiente de volverlo
      obligatorio en el backend se puede cerrar despues de probarlo en serio.

      **Editar y borrar NO pasan por la cola offline**, y es una decision: la
      cola protege el camino rapido (cargar parado en un mostrador), y editar es
      una correccion deliberada que se hace sentado. Encolar modificaciones
      necesitaria orden garantizado y resolucion de conflictos, o sea un log de
      operaciones y no una lista.
- [~] **6.10 — Probarla en el telefono, auditar, y juntar las dos ramas.**
      Esta sesion se trabajo en paralelo desde dos lados y termino en dos ramas
      que habia que reconciliar. Vale como sesion aparte porque lo que encontro
      no lo encuentra escribir features.

      **Lo que salio de probar la app en un telefono de verdad** (Franco):
      - **El 404 que salia como 401**, y por eso la app cerraba sesion sola con
        un token perfecto. Ver la seccion de arriba: la causa esta en
        `OncePerRequestFilter`, no en nada que se vea leyendo el codigo propio.
      - **La sesion zombi** con token vencido, que ahora se cierra sola y navega
        al login.
      - **"Cerrar sesion" no navegaba**, asi que parecia no andar.
      - **No habia pantalla de registro.** El backend sabe registrar desde la
        sesion 4, pero el cliente solo sabia loguear: Viole no podia crearse la
        cuenta desde el telefono. Una app de dos usuarios donde uno no puede
        entrar no esta terminada.
      - **Solo se veia el mes en curso.** El 1 de octubre, septiembre se volvia
        invisible. Ahora hay un selector de mes (`MesProvider` + `SelectorDeMes`)
        compartido por resumen, lista y saldo.

      **Lo que salio de auditar la vaquita**: los tres huecos que estan mas
      arriba (vaquita cerrada inalcanzable, aporte sin deshacer, y el default
      automatico a VAQUITA que publicaba los regalos sorpresa).

      **Lo que salio de juntar las dos ramas**, que es lo mas interesante de los
      tres porque **no estaba roto en ninguna de las dos por separado**: el
      cierre de sesion automatico por 401 llamaba al mismo `salir()` que vacia la
      cola de gastos pendientes. Token vencido + gastos sin mandar = gastos
      borrados en silencio. De ahi sale `MotivoDeSalida`. La leccion, que sirve
      para la entrevista: **dos cambios correctos por separado pueden producir un
      bug al juntarse**, y el unico momento en que se puede ver es leyendo el
      merge, no cada rama.

      Otras decisiones de la reconciliacion: se unifico en `gasto/[id].tsx` (y no
      `gasto/editar?id=`) despues de verificar en `expo-router/build/sortRoutes.js`
      que las rutas estaticas ganan sobre las dinamicas, asi que `gasto/nuevo` no
      compite; `FilaGasto` quedo con el `Pressable` + `disabled` de Franco, que
      evita un bug de la version propia (con `View` el `style` como funcion no se
      evalua); y `registrarse` toma un objeto y no cuatro `string` sueltos.

      **Y el selector de mes obligo a revisar la copy.** Cuatro textos decian
      "este mes" en presente -- "todavia no cargaste nada este mes" -- que era
      cierto mientras solo se podia ver el mes en curso. Mirando agosto en
      octubre, "todavia" promete algo que ya no puede pasar: ese mes cerro. Las
      pantallas ahora eligen la frase con `esElMesActual`. El backend no hizo
      falta tocarlo: `Periodo.transcurridoDe` ya distinguia mes en curso, mes
      cerrado y mes futuro desde la sesion 3.

      **Los tres chequeos que fallaron en la primera corrida del smoke test eran
      del script, no del backend**, y valen como recordatorio de que un test que
      falla no siempre acusa al codigo:
      - `/pozos/activo` sin vaquita devuelve 204, y el script hacia
        `$null -eq $respuesta`. Ante un 204, `Invoke-RestMethod` de PowerShell 5.1
        devuelve un string vacio y no `$null`, asi que la comparacion daba falso
        hiciera lo que hiciera el backend. Ahora se chequea el CODIGO, que ademas
        es el contrato que se queria probar: 204 y no 404.
      - El aporte de cero se chequeaba con `EsperarValidacion`, que exige un
        cuerpo con `errores` -- la forma de Bean Validation. Pero el cero lo
        rechaza `PozoServicio` con una `ReglaDeNegocioException`, cuyo cuerpo es
        solo `{"mensaje"}`. Va con `EsperarRegla`. Y la regla esta donde
        corresponde: Bean Validation no tiene un `@NotZero`, y una anotacion
        propia para un solo campo es mas maquinaria que regla.

      Verificado: **52 tests puros en verde** (`PozoTest`, `CalculadorDeAnimoTest`,
      `PeriodoTest`, `PoliticaDeContrasenasTest` y el `ValidacionDeConfiguracionTest`
      que sumo Franco), `tsc --noEmit` limpio -- tambien con `--noUnusedLocals`,
      que es lo que atrapa los restos de un merge -- y `expo export` bundleando.
      Y el smoke test contra Mongo local: **98 de 101**, con los 3 que fallaban
      corregidos despues (eran del script). Falta re-correrlo para verlos en
      verde, pero ya cumplio lo que mas importaba: que el backend arranque y
      sirva 98 chequeos prueba que el grafo de beans del merge resuelve, que es
      mejor senial que `contextLoads` porque ademas ejercita los endpoints.

      Lo que sigue sin probarse es **la app en un telefono**: la cola en modo
      avion, la direccion de la deuda en un compartido al 70/30, y la vaquita
      real con los dos aportes.

      **Cerrando la sesion se hicieron las dos cosas que quedaban de codigo:**
      - **`ValidacionDeConfiguracion` no corria nunca.** Ver la seccion de
        endurecimiento: el guardian de produccion era un bean del que nadie
        dependia, asi que Mongo explotaba primero. Encontrado arrancando el jar
        con el perfil `produccion` y sin secretos, que es exactamente lo que va a
        pasar la primera vez que Render levante el contenedor.
      - **Los servicios tienen tests.** `GastoServicioTest` (21),
        `PozoServicioTest` (9), `AutenticacionServicioTest` (14) y
        `ResumenServicioTest` (10), con los repositorios mockeados. **107 tests
        en total**, contra 52.
      - **Y ahora corren solos**, en cada push y cada PR
        (`.github/workflows/ci.yml`), con una Mongo de verdad para que
        `contextLoads` tambien entre. Ver "Los tests: dos capas" mas arriba.
- [~] **6.11 — v1.0: lista para la App Store.** Sin producto nuevo: todo lo que
      bloqueaba la revision de Apple, mas lo que encontro Viole usandola.

      **Lo que exige Apple:**
      - **Registro abierto**, con un grupo por cuenta. Arrastro la regla
        `tienePareja`, el rate limit que cuenta exitos, y juntar a Franco y
        Ella por `mongosh` en el smoke test. Ver "Registro abierto" arriba.
      - **Borrar la cuenta desde la app** (guideline 5.1.1(v)), con lo que
        promete la politica de privacidad. Ver "Borrar la cuenta" arriba.
      - **Politica de privacidad** linkeada desde Ajustes, y **pagina de
        soporte** en `docs/soporte.md`.
      - **Sin iPad** (`supportsTablet: false`): ninguno de los dos lo usa, y con
        iPad prendido habia que subir capturas de iPad y el revisor la probaba
        en un layout que nadie miro nunca.

      **Lo que la hace parecer una app y no un prototipo:** el icono de iOS
      (procesado desde el original: recortado al cuadrado y con las esquinas
      rellenadas, porque iOS pone su propia mascara), la splash con la nutria
      contenta que se queda hasta que cargan las fuentes, y la ortografia de
      toda la copy (51 textos de la app y 28 mensajes del backend).

      **Lo que encontro Viole usandola:** la descripcion paso a ser opcional
      -- ver su seccion -- y la app escribia "senial" en vez de "señal".

      **Lo que quedo listo para mandar:** `docs/app-store.md` tiene todos los
      textos de App Store Connect (verificados contra los limites de cada
      campo), las respuestas de privacidad, las notas para el revisor y los
      pasos en orden. `scripts/crear-cuenta-demo.ps1` arma la cuenta del
      revisor con gastos creibles (la misma sirve para las capturas, asi no se
      ven gastos reales), y es idempotente: cada gasto lleva un `clienteId`
      fijo, la misma clave que protege la cola offline.

      **Un bug propio que vale como leccion:** al agregar una seccion de este
      archivo, el texto "El \`$\` posicional" paso por un `String.replace` de
      JavaScript, donde `` $` `` significa "todo lo anterior al match", y se
      inserto media copia del archivo en el medio. Llego a `main`. Cuando se
      reemplaza texto que no controlas, va `split/join` o una funcion como
      reemplazo, nunca un string.

      **Y otro, que rompio el primer build de EAS:** `npx expo install` con el
      npm 11 local **podo** del `package-lock.json` una dependencia opcional
      anidada (`expo/node_modules/react-native-worklets`). El npm 10 de los
      servidores -- el del CI y el de EAS -- la espera, y su `npm ci` falla con
      `EUSAGE` antes de instalar nada. El CI lo aviso en rojo y nadie lo miro
      antes de lanzar el build. Se arreglo devolviendo esa entrada al lock y se
      verifico con `npx npm@10.9 ci` en una carpeta aparte. **Despues de tocar
      dependencias, mirar el CI antes de buildear.**

      Verificado: 121 tests, el smoke test (145 chequeos) contra Mongo local, y
      el CI en verde. Lo que falta es de un telefono y de App
      Store Connect: ver los pasos de `docs/app-store.md`.
- [ ] **7 — Build EAS y TestFlight.** Los dos tienen iPhone 13 Pro y la cuenta
      de Apple Developer ya existe. Va **TestFlight interno** (Viole como
      usuaria en App Store Connect), que no pasa por Beta App Review; subirla a
      la App Store es App Review de verdad, y una app con login sin usuario
      demo se rechaza por la guideline 2.1. Ver tambien **`docs/atajo-ios.md`**:
      un atajo con Back Tap que le pega a `POST /gastos` sin abrir la app.
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

# Levantar la API para correr el smoke test (registra varios usuarios seguidos)
cd backend; $env:REGISTRO_MAX_POR_IP = "100"; .\mvnw.cmd spring-boot:run
.\scripts\smoke-test.ps1

# Correr los tests
cd backend; .\mvnw.cmd test

# Consola de MongoDB
docker exec -it gastos-mongo mongosh -u gastos -p gastos_local --authenticationDatabase admin gastos
```
