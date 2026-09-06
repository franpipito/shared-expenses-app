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

**Pendiente de definir (sesion 3):** cual es exactamente la regla, cuantos
estados de animo hay, y por lo tanto que datos tiene que devolver
`GET /gastos/resumen` para alimentarla.

**Pendiente de conversar:** que tan dura es la nutria enojada. Es una app de
finanzas para alguien que ya se siente mal cuando se queda sin plata antes de
cobrar; una app que la haga sentir culpable se desinstala. "Preocupada" y
"orgullosa cuando mejoras" suele sostener mas el uso que "enojada". Decision de
Franco, que conoce a la usuaria.

**Compromiso pendiente:** cuando arranque la etapa de front, entregarle a Franco
un prompt para v0.dev (Vercel) que genere el mockup con las nutrias.

## Stack

| Pieza    | Tecnologia                                    | Estado |
|----------|-----------------------------------------------|--------|
| Backend  | Java 21 + Spring Boot 4.1.1 + PostgreSQL 17   | en curso |
| DB local | Docker Compose (`docker compose up -d`)       | en curso |
| Mobile   | Expo + React Native + TypeScript              | pendiente |
| Web      | React + Vite + TypeScript + Tailwind          | pendiente |
| Deploy   | Railway o Render + Postgres gestionado        | pendiente |

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

Consecuencia para la sesion 2: `GET /gastos` necesita saber **quien pregunta**, y
la autenticacion recien llega en la sesion 4. Se resuelve con una costura: una
abstraccion chica tipo `UsuarioActual` que en la sesion 2 lea un header y en la 4
lea el JWT, sin tocar el servicio. Discutirlo al abrir la sesion 2.

No tienen economia compartida (ingresos separados). El encuadre de la seccion de
pareja es **"quien le debe a quien"**, no "nuestra plata".

### `descripcion` es obligatoria
La usuaria la eligio como uno de sus tres campos: "algo que me recuerde el
momento". Es lo que le permite distinguir despues el gasto evitable del que no lo
era. No es decorativa.

### Concurrencia: bloqueo optimista con `@Version`
`Gasto` tiene un campo `@Version`. Hibernate agrega `AND version = ?` a cada
UPDATE. Si los dos integrantes editan el mismo gasto a la vez, la segunda
escritura falla en vez de pisar la primera en silencio.

### Schema: `ddl-auto=update` por ahora
Comodo para desarrollar. **Migrar a Flyway antes del deploy (sesion 5).**

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

### Pendiente de decidir
- **Cuando hacer obligatorio el `version` en el PUT.** Hoy es opcional: si el
  cliente lo manda, se verifica; si no, gana la ultima escritura. Conviene
  volverlo obligatorio cuando la app mobile este armada y sepamos que siempre lo
  reenvia.
- **`password_hash` viaja de la base a la app en cada listado de gastos**, porque
  el `join fetch g.pagadoPor` trae la entidad Usuario entera. No sale por la API
  (el DTO no lo incluye), pero es dato sensible moviendose sin necesidad. Se
  arregla con una proyeccion o marcando el campo como lazy. No es urgente.
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
    seguridad/    UsuarioActual (la costura que en la sesion 4 pasa a JWT)
    error/        excepciones de dominio + @RestControllerAdvice
/mobile       Expo (sesion 6)
/web          React + Vite (despues del MVP)
docker-compose.yml       Postgres local
scripts/
  seed-desarrollo.sql    categorias, grupo y usuarios de prueba
  smoke-test.ps1         29 chequeos de la API contra el backend corriendo
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
- [ ] **4 — Autenticacion.** Login con JWT.
- [ ] **5 — Deploy.** Railway o Render + Postgres gestionado + Flyway + env vars.
- [ ] **6 — App Expo minima.** Contra la API deployada, no localhost.
- [ ] **7 — Build EAS y TestFlight.**

## Comandos

> **El autor trabaja en PowerShell 5.1 en Windows.** Ahi NO funcionan `&&`,
> `||`, ni `<` para redirigir entrada, ni los operadores `?:` y `??`. Darle
> siempre los comandos en sintaxis compatible: `;` para encadenar,
> `Get-Content archivo | comando` en vez de `comando < archivo`, y `.\mvnw.cmd`
> en vez de `./mvnw`. Ojo tambien con `curl`, que en PowerShell es un alias de
> `Invoke-WebRequest`: para el curl de verdad va `curl.exe`.

```powershell
# Levantar Postgres
docker compose up -d

# Cargar los datos de desarrollo (categorias, grupo y usuarios de prueba)
Get-Content scripts/seed-desarrollo.sql | docker exec -i gastos-postgres psql -U gastos -d gastos

# Levantar la API
cd backend; .\mvnw.cmd spring-boot:run

# Correr los tests
cd backend; .\mvnw.cmd test

# Consola de Postgres
docker exec -it gastos-postgres psql -U gastos -d gastos
```
