# La vaquita del viaje

Diseño del **pozo compartido**: una bolsa de plata a la que los dos aportan y
de la que salen los gastos de un viaje. El caso que la motivó es Bariloche:
$800.000 al pozo, $400.000 cada uno, y durante el viaje los gastos se descuentan
de ahí.

Este archivo es la fuente de verdad de **qué se decidió y por qué**. Las
decisiones de producto generales viven en `docs/entrevista-usuaria.md`; los
colores y la estructura de carpetas, en `docs/diseno.md`.

---

## 1. Por qué esto no contradice la entrevista

Toda la app está construida sobre la respuesta 18: *"es como economía
compartida, pero nosotros no tenemos eso"*. El pozo **es** economía compartida,
así que conviene decir con precisión por qué entra igual.

El pozo es economía compartida **acotada y con fecha de vencimiento**. No es
"nuestra plata", es "nuestra plata para Bariloche". Fuera del viaje, la app
sigue siendo lo que ella describió: tu plata, mi plata, y quién le debe a quién.

Si esa frontera se difumina —si el pozo empieza a ser permanente, o a mostrarse
en el resumen mensual— la app deja de ser la que la usuaria pidió. Por eso el
pozo tiene `desde` y `hasta`, y por eso no aparece en ninguna pantalla mensual.

## 2. El pozo NO es un presupuesto

Importa poder decir esto, porque los presupuestos están descartados (respuesta
14: ingresos freelance irregulares, un tope fijo mensual no le sirve).

Un presupuesto es **un tope sobre plata que todavía no tenés**, y que con
ingresos variables puede no entrar nunca. El pozo es **plata que ya existe, que
ya pusieron y que ya está ahí**.

El número que muestra la pantalla no dice *"te queda permitido gastar $X"*.
Dice *"queda $X"*. Es un hecho, no una meta. Por eso el pozo entra donde los
presupuestos no.

## 3. El pozo es la `Liquidacion` que faltaba

`dto/SaldoRespuesta.java` ya lo tenía anotado: *"si algún día quieren llevar la
cuenta en serio, la solución es una entidad `Liquidacion` y saldo = deudas −
pagos"*.

**Un aporte al pozo es una liquidación anticipada.** Poner $400.000 cada uno es
pagarse por adelantado gastos que todavía no se hicieron. No hacen falta dos
conceptos: el pozo es una cuenta virtual, los aportes son los créditos y los
gastos con `pozoId` son los débitos.

### El invariante, que es todo el modelo

```
restante         = Σ aportes − Σ gastos con pozoId
deuda entre ellos = (aportó Franco − aportó Viole) / 2
```

La segunda línea es la que importa: **sacar del pozo NO genera deuda entre
ellos**, porque la plata ya se repartió al entrar. Si los dos ponen $400.000, el
saldo entre ellos es cero desde el minuto uno y sigue en cero todo el viaje,
gasten lo que gasten. Si Franco pone $500.000 y Viole $300.000, esos $100.000 de
diferencia se registran **una sola vez, al aportar**.

Es la misma filosofía que `montoPagador`: **resolver al escribir para no dividir
al leer.**

## 4. La app no custodia plata

El pozo no es una cuenta bancaria y no puede serlo. Custodiar plata ajena
significa PCI, prevención de fraude y ser sujeto obligado ante la UIF. Ninguna
app de dos personas quiere estar ahí.

**La plata vive donde ellos decidan** (una cuenta conjunta real, o los $800.000
en un Mercado Pago de uno solo). MiNutria es **el libro contable de esa cuenta**:
dice cuánto queda y quién puso qué.

Esa separación es deliberada y tiene una consecuencia buena: la decisión
bancaria y la decisión de software quedan independientes. Pueden cambiar de
banco sin tocar una línea.

## 5. El modelo

### Las tres opciones que se evaluaron

**A — `TipoGasto.POZO`, un tercer valor del enum. Descartada.**
Parece una línea y toca todos los condicionales que leen ese campo. `tipo` hoy
es binario y gobierna **dos cosas a la vez**: en qué sección aparece el gasto y
quién puede verlo. Cada `Criteria.where("tipo").is(COMPARTIDO)` y cada
`if (tipo == PERSONAL)` pasaría a ser una decisión de tres ramas, y `saldoDe()`
incluiría o excluiría el viaje según una línea fácil de olvidar. Es la trampa
clásica de agregarle un valor a un enum que ya carga significado.

**B — Colección de movimientos aparte, los gastos del viaje no son gastos.
Descartada.**
Los gastos de Bariloche desaparecerían del listado del mes y de `porCategoria`,
haría falta un **segundo formulario de carga** (lo único que la app no puede
pagar, por el requisito de velocidad) y se duplicarían visibilidad, validación y
bloqueo optimista. Acierta en una cosa, igual: el viaje sí es un libro aparte, y
por eso su **lectura** va separada aunque la **escritura** no lo esté.

**C — `pozoId` opcional en `Gasto` + un documento `Pozo` chico. Elegida.**

### El documento

```
pozo {
  _id, grupo_id, nombre: "Bariloche", objetivo: 800000.00,
  estado: ABIERTO | CERRADO, desde, hasta,
  aportes: [ { usuario: { usuarioId, nombre }, monto, fecha } ],
  creado_en, actualizado_en, version
}
```

Los aportes van **embebidos**, por el mismo criterio que `ReferenciaUsuario`:
son pocos, están acotados (dos personas, un puñado de aportes por viaje) y
siempre se leen junto al pozo. Nunca se consultan solos.

El aporte embebe un `ReferenciaUsuario`, el mismo snapshot que usa
`Gasto.pagadoPor`, en vez de repetir los campos sueltos. **El campo se llama
`usuarioId` y no `id`** por el motivo documentado ahí: Spring Data convierte
cualquier propiedad llamada `id` a ObjectId, también dentro de un documento
embebido, y en silencio.

`Aporte` es un `record`, o sea inmutable, que es lo correcto para un asiento
contable: un aporte no se edita; si estuvo mal, se compensa con otro.

### El detalle de Mongo que importa: `$push`, no read-modify-write

Un aporte **no** se agrega con `pozo.getAportes().add(...)` + `save()`. Si los
dos aportan al mismo tiempo, el segundo `save()` pisa al primero sin que nadie se
entere: los dos leyeron la misma lista de dos elementos y los dos escriben una
lista de tres.

Va con `$push` atómico:

```java
mongoTemplate.updateFirst(
    Query.query(Criteria.where("_id").is(pozoId).and("estado").is(ABIERTO)),
    new Update().push("aportes", aporte),
    Pozo.class);
```

Es una escritura de **un solo documento**, y en Mongo eso ya es atómico — el
mismo argumento por el que no hay `@Transactional` en ningún servicio. En
Postgres el aporte sería un `INSERT` en una tabla hija; acá el array *es* la
tabla hija y `$push` es el insert.

**Y acá hay una trampa que costó encontrar pensándola:** `updateFirst` a mano no
toca el `@Version`. Si lo dejáramos así, alguien que leyó el pozo *antes* del
aporte podría después llamar a `save()`, su versión seguiría coincidiendo, y
pisaría la lista de aportes con la copia vieja — o sea, **borraría el aporte
recién hecho sin ningún error**.

Por eso el update lleva `.inc("version", 1)`, que es exactamente lo que haría
`save()`. De ahí sale la regla de la clase: *después de crearlo, un `Pozo` no se
guarda nunca más con `save()`.* Todo cambio es una actualización condicional.

## 6. Qué toca del código que ya funciona

Casi nada, y esa era la idea:

| Archivo | Cambio |
|---|---|
| `modelo/Gasto.java` | un campo `pozoId` nullable |
| `repositorio/GastoConsultasImpl.java:141` (`saldoDe`) | un `Criteria.where("pozo_id").is(null)` |
| `repositorio/GastoConsultasImpl.java:116` (`sumarHormigaDe`) | idem |
| `repositorio/GastoConsultasImpl.java:132` (`contarEn`) | idem |
| `repositorio/GastoConsultas.java` | dos consultas nuevas: `buscarDelPozo` y `sumarDelPozo` |
| `servicio/GastoServicio.java` (`crear` y `actualizar`) | validar el pozo y **rechazar** los `PERSONAL` |
| `dto/GuardarGastoRequest.java` | `String pozoId` opcional |

### Un gasto PERSONAL con `pozoId` se rechaza, no se corrige

Es tentador "arreglarlo" promoviéndolo a COMPARTIDO, y sería un error grave.

Un gasto PERSONAL es privado de quien lo carga. Promoverlo en silencio
**publicaría un gasto que su dueño marcó como privado**, y el caso concreto es
justo el que la usuaria pidió cuidar en la entrevista: un regalo sorpresa
cargado por error con la vaquita puesta. Un 400 es molesto; filtrar el regalo
rompe el producto.

Es la regla general de este backend: ante un input ambiguo, fallar, no adivinar.

**Nada más.** La regla de visibilidad (`visiblesPara()`, línea 54) no se toca:
un gasto del pozo es un `COMPARTIDO` normal con una marca, así que los dos lo
ven por la regla que ya existe. El bloqueo optimista, el listado y las
categorías funcionan tal cual.

### Un `null` de Mongo que en SQL sería un bug

`Criteria.where("pozo_id").is(null)` matchea **tanto los documentos que tienen el
campo en null como los que no lo tienen**. Eso es lo que queremos: los gastos ya
cargados no tienen el campo, y Spring Data tampoco lo escribe cuando es null.

Vale tenerlo presente porque en SQL es al revés: `WHERE pozo_id = NULL` no
matchea nunca y hay que escribir `IS NULL`. Acá el comportamiento por defecto es
el que sirve, pero conviene saber que es una decisión de Mongo y no una
casualidad.

### Índice

Un `@CompoundIndex` en `{pozo_id: 1, fecha: -1}` para listar los gastos del
viaje ordenados. El filtro `is(null)` de los tres agregados mensuales no lo usa
—sigue montado sobre `idx_gasto_grupo_fecha`— y está bien: descarta sobre un
conjunto que ya viene filtrado por grupo y por mes.

Si algún día la colección crece mucho, la mejora es un **índice parcial** que
solo incluya los documentos que tienen `pozo_id`, porque la enorme mayoría no lo
va a tener.

## 7. La API

```
POST   /pozos                  { nombre, objetivo?, desde?, hasta? }   201
GET    /pozos/activo           ->  PozoRespuesta  |  204 si no hay
GET    /pozos/{id}/gastos      ->  los gastos del viaje, sin recorte por mes
POST   /pozos/{id}/aportes     { monto }                               200
POST   /pozos/{id}/cerrar                                              200
```

```
PozoRespuesta {
  id, nombre, objetivo, estado, desde, hasta, vigente,
  aportado, gastado, restante,
  porPersona: [ { usuarioId, nombre, total } ],
  aportes:    [ { usuario: {...}, monto, fecha } ],
  version
}
```

Tres decisiones de la API que vale justificar:

- **`GET /pozos/activo` devuelve 204 y no 404 cuando no hay ninguna.** No tener
  vaquita es un estado normal de la app, no un error. El cliente pregunta "¿hay
  una?" y "no" es una respuesta válida, no un fallo que mostrarle a nadie.
- **`vigente` lo calcula el backend**, aunque sea una comparación de fechas. Es
  el mismo motivo que el bean `Clock`: "hoy" depende de la zona horaria, y el
  servidor corre en UTC.
- **`porPersona` lista a los dos integrantes, incluso al que aportó cero.**
  "Viole $0" es información útil, no un hueco: es justo lo que hay que mirar
  antes de salir de viaje.

`GET /pozos/{id}/gastos` existe aparte, en vez de un parámetro en `GET /gastos`,
porque su consulta tiene otra semántica: no se corta por mes. Meterle un
parámetro que cambia el modo a un endpoint existente es cómo se pudren los
endpoints.

`aportado`, `gastado` y `restante` se calculan **al vuelo**, con un `$sum`
filtrando por `pozo_id`, consistente con la decisión de no materializar
agregados (ver CLAUDE.md). El pozo de un viaje son decenas de documentos.

**Regla de negocio: un solo pozo ABIERTO por grupo.** Saca la pregunta "¿a qué
pozo?" del formulario de carga, que es donde cada decisión se paga en abandono.
Es una restricción que se puede levantar después sin migrar nada.

## 8. Las decisiones que no se preguntaron y hay que tomar igual

### El ánimo de la nutria: los gastos del pozo NO cuentan

El viaje arranca cerca del 30 de septiembre, así que parte el mes al medio y
ensucia la comparación de septiembre **y** la de octubre. `CalculadorDeAnimo`
compara tramo contra tramo con una banda de ±10%; un viaje es cien veces esa
banda.

Sin hacer nada, **la nutria estaría PREOCUPADA durante las vacaciones que
ahorraron**. Eso es exactamente el riesgo número uno del proyecto: una app que
te hace sentir culpable se desinstala.

Decisión: los gastos con `pozoId` salen del cálculo del ánimo y del total
hormiga mensual. Se pueden seguir marcando como hormiga (un souvenir carísimo lo
es) pero ese número vive en la pantalla del viaje.

Tres razones, en orden de peso:

1. **Producto.** La nutria enojada en el viaje es la peor versión de la app.
2. **Estadística.** Comparar un mes con viaje contra uno sin viaje no es una
   tendencia, es ruido.
3. **Conceptual.** El gasto hormiga se definió como *"lo que mirado en frío
   podría no haberse hecho"*. Plata que ahorraron a propósito para un viaje **ya
   se miró en frío**. Por definición no es hormiga.

Lo que se pierde, y hay que decirlo: un viaje donde se pasaron no se refleja en
la nutria. Está bien — esa historia la cuenta la pantalla del viaje.

Efecto lateral elegante: **`pozo_id == null` pasa a significar "gasto de la vida
normal"**, y ese es el filtro de los tres agregados mensuales. Un concepto, un
campo, tres lugares.

### El formulario: el pozo no agrega un campo, reemplaza uno

Acá es donde esta feature puede matar el producto. El alta tiene tres campos más
el toggle de hormiga, y cada campo que se agregue se paga en abandono.

Con un pozo abierto, el bloque "Es un gasto compartido" pasa a ser tres chips:
**Personal / Compartido / Vaquita**. Elegir Vaquita es **más rápido** que elegir
Compartido, porque no hay que decidir reparto ni quién pagó: pagó el pozo. Un
tap en vez de tres.

**El default:** si hay un pozo abierto **y hoy cae entre sus fechas**, el
formulario abre con Vaquita puesta. En Bariloche el 90% de lo que carguen sale
del pozo, así que el camino rápido queda más corto que hoy.

Se ata a las fechas y no a que el pozo exista, para que el café que se compra
Viole sola en octubre no se cargue sin querer al viaje.

### El sobregiro no se bloquea

Si el pozo se queda sin plata en medio de una cena, **el alta no se rechaza**.
El pozo queda en negativo y la pantalla dice "se pasaron por $X".

Bloquear una carga parada en el mostrador es el pecado capital de esta app. Y
además el rojo es lo que pasa en la realidad: si se acabó la vaquita, siguen
gastando y lo arreglan después.

La regla general que vale más que el caso: **validar no es lo mismo que
bloquear.** Un monto negativo es imposible y se rechaza; un pozo en rojo es
posible y se muestra.

### Lo visual

- El número grande es **"queda"**, y **no va en ámbar**. El ámbar es del gasto
  hormiga y de nada más (ver `docs/diseno.md`). Va teal, que es lo compartido, y
  terracota cuando el restante es negativo — que es el color de "mirá esto",
  tampoco ámbar.
- Es la segunda pantalla que se gana la ilustración de las dos nutrias
  (`nosotros.png`), que Viole pidió explícitamente al evaluar los mockups.
- La pantalla del viaje **no es mensual**. Es el primer agregado de la app que
  no se corta por mes, y está bien: el viaje trae su propio corte natural. Eso
  es justamente lo que le faltaba al saldo histórico para no ser un número que
  solo crece.

## 8.b Cómo quedó en la app

**`app/vaquita.tsx`** es una pantalla con dos modos, y el vacío **es un
formulario, no un cartel**: como abrir la vaquita se hace una sola vez, la
pantalla vacía ya es el formulario para abrirla, en vez de un cartel que obliga a
un tap más. Solo pide el nombre; el objetivo es opcional, y dice explícitamente
que *no es un tope* — un número que parece un límite se lee como presupuesto, y
los presupuestos están descartados.

Con vaquita abierta muestra el restante grande, cuánto puso cada uno (**los dos
siempre, incluso el que puso cero**), un campo para poner plata, la lista de
gastos del viaje y el cierre. Cerrar pasa por un `Alert` de confirmación: es lo
único irreversible de la app, porque no hay endpoint para reabrir un pozo.

**El alta de gasto cambia de forma según haya vaquita o no**, y es deliberado:

- **Sin vaquita abierta** —o sea casi todo el año— el control sigue siendo el
  switch de siempre. Esa pantalla no cambió en nada.
- **Con vaquita abierta** pasa a ser tres chips: Personal / Compartido /
  Vaquita. Un switch no tiene tres estados, y meter un segundo switch traería
  combinaciones imposibles.

El booleano `esCompartido` se reemplazó por un tipo `Destino` de tres valores.
**`VAQUITA` no existe en el backend**: allá el gasto sigue siendo `COMPARTIDO` y
lo único que lo distingue es tener `pozoId`. El tipo vive solo en la pantalla,
que es donde la pregunta se hace una vez.

Y la lista del viaje reusa `FilaGasto` tal cual, con lo que viene gratis lo que
importa: **la marca ámbar de gasto hormiga se sigue viendo gasto por gasto**. Un
souvenir carísimo es hormiga aunque haya salido de la vaquita; lo que no hace es
contar para el total del mes.

**Un atajo mas, agregado despues: "+ Agregar gasto" arriba de `vaquita.tsx`.**
El default por fecha (arriba) no cubre comprar algo del viaje antes de que
arranque o despues de que termine. El boton no abre un formulario nuevo --
navega al de siempre con el chip Vaquita como punto de partida
(`FormularioDeGasto.destinoSugerido`). Detalle completo en CLAUDE.md, "Un
atajo para cargar un gasto desde adentro de la vaquita".

## 9. Lo que queda afuera a propósito

**El cierre del pozo con devolución del sobrante.** Es una liquidación de verdad,
con su flujo y su pantalla. Para el viaje no hace falta: vuelven, ven que
sobraron $120.000 y se lo transfieren. El campo `estado` queda en el documento;
la pantalla de cierre, no.

> **El pozo no necesita resolver la devolución para servir en el viaje.** Saber
> qué pedazo del problema no hay que resolver todavía es la mitad del trabajo.

Tampoco: múltiples pozos simultáneos, metas de ahorro, el pozo dentro del
resumen mensual, ni notificaciones de "queda poco".

**El número del desbalance tampoco se calcula, y es una decisión, no un olvido.**
Si Franco pone $500.000 y Viole $300.000, el invariante dice que ella le debe
$100.000. Hoy la API devuelve los dos totales en `porPersona` y nada más: con dos
personas mirando una pantalla que dice "Franco $500.000 / Viole $300.000", la
cuenta la hacen ellos. Exponerlo como número propio agrega un concepto nuevo a la
UI ("la vaquita te debe") que todavía no se diseñó. Queda anotado para cuando la
pantalla exista.

**Esto sigue sin resolverse.** Lo que se agregó después (sección 10) es un
desglose de GASTO -- quién pagó cada cosa que salió del pozo --, no de aporte:
no hay que confundirlos. El de aporte es el único que mueve el invariante; el
de gasto es pura información.

### Qué se tomó de BBVA y qué no

El modelo de referencia fue la cuenta compartida de BBVA, que combina una capa
bancaria con un gestor de gastos estilo Splitwise. Módulo por módulo:

| Módulo de BBVA | Qué hacemos |
|---|---|
| **Identidad y roles** (cotitularidad, firma indistinta, permisos) | **Ya está.** "Los dos ven y escriben los COMPARTIDO" es `visiblesPara()`. Admin vs. miembro solo importa con tres o más, y el registro rechaza al tercero a propósito. Código a escribir: cero. |
| **Registro y transacciones** (importar movimientos, categorizar con IA, adjuntar comprobantes) | **Nada.** Ver abajo. |
| **Lógica de reparto** (equitativa, asimétrica, simplificación de deudas) | **Ya está.** El 50/50 por defecto y los chips de reparto. La simplificación de deudas **con dos personas es la función identidad**: el algoritmo reduce un grafo al mínimo de transferencias, y con dos nodos hay una sola arista ya simplificada. Implementarlo sería puro currículum. |
| **Liquidación y notificaciones** | **Acá sí faltaba algo**, y es el pozo: los aportes SON las liquidaciones. Las notificaciones push quedan fuera de alcance. |

### Por qué no se importan los gastos de Mercado Pago

Se evaluó y se descartó, por dos motivos independientes. El segundo es el que
manda.

**No se puede, técnicamente.** La API pública de Mercado Pago es de *cobros*
(Checkout, QR, Point): no hay endpoint soportado ni scope de OAuth para que un
tercero lea los movimientos salientes de una cuenta personal. Y en iPhone no hay
atajo por el costado: leer las notificaciones de otra app es
`NotificationListenerService`, que es de Android. Queda el parseo de mails
("Pagaste $X en COMERCIO"), que funciona pero llega con retraso variable y se
rompe en silencio cuando cambia el formato.

**Y aunque se pudiera, no lo querríamos.** De los cuatro campos que la usuaria
eligió, una importación automática solo puede completar dos:

| campo | ¿lo sabe MP? |
|---|---|
| monto | sí |
| fecha | sí |
| categoría | a veces, adivinando por el comercio |
| **descripción** | **no** — da `RAPPI*BARILOCHE`, no "algo que me recuerde el momento" |
| **esHormiga** | **no, nunca** |

El gasto hormiga es *"un juicio que solo puede emitir quien carga el gasto, en el
momento de cargarlo"*. Ninguna máquina lo infiere: el mismo Uber por el mismo
monto es hormiga o no según por qué lo tomaste.

Así que la importación automática no elimina trabajo, **lo mueve**: de 5 segundos
en el mostrador a 20 minutos a la noche revisando una bandeja de gastos a medio
llenar, tratando de acordarse si el café de las 4 era evitable. Esa revisión
diferida es exactamente la tarea que la gente abandona. Es el Excel otra vez,
con más pasos.

**Y rompe el único pedido de privacidad que hizo la usuaria.** No todo lo que se
paga con MP en Bariloche sale del pozo: un regalo para Viole entraría
automáticamente en la vaquita compartida y ella lo vería. Respuesta 16, textual:
*"sí, más que nada cuando te hago regalitos"*.

La alternativa que sí se adoptó, y que ataca el problema real (la velocidad de
carga) sin ninguna de estas contras, está en **`docs/atajo-ios.md`**.

## 10. Quién gastó cuánto, al lado de quién aportó cuánto

Surgió de una pregunta de Franco pensando en el viaje, no de la entrevista:
*"si aportan los dos lo mismo y uno gasta más que el otro, hoy no se sabe quién
gastó más."* Tiene razón, y es una pregunta DISTINTA de la que ya contesta
`porPersona` (sección 9): esa es sobre APORTES (cuánto puso cada uno), y sí
afecta el invariante -- si aportaron distinto, hay una deuda. Esta es sobre
GASTOS (quién pagó cada cosa que salió del pozo), y **no afecta nada**: la
plata ya es de los dos desde que entró al pozo, así que no importa quién la
gastó.

**Decisión (discutida antes de programar, regla 3): puramente informativo,
igual que `porPersona`.** Se evaluó que la diferencia de gasto generara una
deuda nueva, y se descartó: rompería el invariante central de toda la vaquita
(*"sacar plata del pozo no genera deuda entre ellos, porque la plata ya se
repartió al aportar"*). Si Franco gasta $300.000 del pozo y Viole $0, eso NO
significa que Viole le deba a Franco -- los dos ya pusieron lo mismo. Mezclar
las dos preguntas (quién aportó / quién gastó) en un solo número de deuda
confundiría una de HISTORIA ("¿en qué se fue la plata?") con una de
CONTABILIDAD ("¿quién le debe a quién?"). Mismo criterio que `porPersona`: con
dos personas mirando dos números, la cuenta la hacen ellos.

**De dónde sale el dato**: cada `Gasto` de un pozo ya guarda `pagadoPor` --
quien lo cargó, no quien "lo disfrutó" -- igual que cualquier otro gasto. No
hizo falta ningún campo nuevo, solo agruparlo: `GastoConsultas.
gastadoPorPersonaDelPozo(pozoId)` es un `$match` + `$group by pagadoPor.
usuarioId` (mismo pipeline que `sumarDelPozo`, con una fila por persona en vez
de una sola con el total de todos). `PozoServicio.gastadoPorPersona(pozo)` lo
combina con los integrantes del grupo exactamente como `totalesPorPersona`
combina los aportes: los dos SIEMPRE (incluido el que gastó cero), más quien
gastó del pozo y ya no está en el grupo -- mismo caso que "Cuenta eliminada"
en los aportes.

En mobile, un bloque "Quién gastó cuánto" al lado de "Quién aportó cuánto", en
`vaquita.tsx` (la vaquita abierta) y `viaje/[id].tsx` (un viaje cerrado):
mismo componente de fila, mismos datos ya resueltos por el backend.

## 11. Cuánto le queda a cada uno, al cerrar el viaje

Pedido de Franco, un rato después de la sección 10: *"quiero que a cuando se
cierra la vaquita le aparezca cuánto le sobró a cada uno, en base a cuánto
aportó menos lo que gastó, para poder sacar del pozo lo que le corresponde a
cada uno."* A diferencia de `porPersona` y `gastadoPorPersona` (sección 10),
que son puramente informativos, **este número SÍ decide plata real**: es la
devolución al cerrar.

**La primera versión de la pregunta tenía un problema, y se discutió antes de
programar nada (regla 3).** La fórmula (`aporte - gastado por esa persona`)
puede dar negativo: si Franco paga la mayoría de las cosas del viaje con la
tarjeta del pozo, su número puede quedar negativo aunque la plata se haya
gastado en los dos por igual -- la fórmula ata el sobrante a QUIÉN PAGÓ, no a
quién se benefició del gasto. Un número negativo no se puede "sacar del pozo"
literalmente: es una deuda, no un retiro.

**La primera reacción de Franco fue bloquear**: que la app no deje cargar un
gasto si a esa persona ya no le queda aporte disponible. Se descartó, y por
un motivo que ya estaba escrito en este mismo documento antes de que la
pregunta existiera: *"Bloquear una carga parada en el mostrador es el pecado
capital de esta app."* Es la razón de ser del proyecto -- Viole abandonó un
Excel anterior por exactamente este tipo de fricción -- y meter una
validación de "no te alcanza, no podés cargarlo" la reintroduce en el peor
momento posible: parada en el mostrador esperando pagar.

Ayudó ver que **el pozo entero ya hace esto mismo, a propósito**: `restante`
puede dar negativo (sección "sobregiro" más arriba, probada en el smoke
test), y nadie lo lee como un bug. `sobrantePorPersona` es la misma idea un
nivel más abajo: se permite el número negativo, se muestra tal cual, y las
dos personas arreglan la diferencia fuera de la app -- la devolución del
sobrante sigue siendo manual, como ya decía la sección 9 ("vuelven, ven que
sobraron $120.000 y se lo transfieren"), solo que ahora el número por persona
les dice CÓMO dividir ese transfer en vez de tener que adivinarlo.

**La implementación no hizo ninguna consulta nueva**: `sobrantePorPersona` es
`porPersona` menos `gastadoPorPersona`, los dos ya calculados. La única
sutileza es que las dos listas no necesariamente tienen a las mismas
personas -- alguien pudo haber aportado sin cargar nunca un gasto del pozo, o
cargar un gasto del pozo sin haber aportado nada -- así que
`PozoServicio.sobrantePorPersona` une los dos conjuntos de ids y toma cero
del lado que falte, en vez de dejar a alguien afuera.

Verificado con el ejemplo exacto de la discusión (los dos aportan $400.000,
uno paga $600.000 del pozo y el otro $100.000): el test unitario prueba que
da -$200.000 / $300.000, y que los dos suman el mismo restante que el pozo
entero. Y se corrió además contra Mongo real, con el pozo ya CERRADO, dando
el mismo resultado.

En mobile, un tercer bloque -- "Cuánto le queda a cada uno" -- pero **solo en
`viaje/[id].tsx`** (el viaje cerrado), no en `vaquita.tsx`: Franco lo pidió
así ("a cuando se cierra"), y tiene sentido -- mientras el viaje sigue
abierto, quién pagó qué todavía puede cambiar. El número se muestra con el
signo puesto, sin `Math.abs()`, mismo criterio que ya se adoptó para "Mi
Plata" en negativo: un número con el signo se lee más como una cuenta real,
y no esconde que alguien tiene que devolver plata.

## 12. El aporte se edita y se borra de verdad, tocando la fila

Pedido de Franco: que un aporte a la vaquita se corrija igual que ya se
corrige un ingreso de "Mi Plata" (CLAUDE.md, sección 2.3c) -- tocar la fila
del historial, no cargar un asiento en contra. Al agregarlo, se borró el
botón "Me equivoqué: sacar esta plata del pozo", que era la UI de esa
corrección por signo.

### Rompe a propósito la consistencia con `Liquidacion`

Con este cambio, `Aporte` deja el grupo de los ledgers inmutables y se suma
al de `Ingreso`. En ese momento, **`Liquidacion` quedó sola** como el único
ledger sin ningún mecanismo de corrección: `liquidaciones.tsx` no tenía ni
edición ni el asiento-en-contrario. (Poco después ganó edición y borrado
también, ver CLAUDE.md -- "Saldar deudas: editable" -- pero sin poder tocar
la dirección, que sigue siendo la diferencia de fondo con `Aporte`/`Ingreso`.)

La razón para no mover a los tres juntos es la misma que ya separaba a
`Ingreso` de `Aporte`/`Liquidacion` en la sección 2.3c: importa el TRAZO
del hecho, no solo el valor final. Un aporte a la vaquita es "puse esta
plata", un hecho de una sola persona -- si el monto se tipeó mal,
corregirlo no pierde nada. Una `Liquidacion` registra DOS personas y una
DIRECCIÓN ("Franco le pagó a Viole"), y esa dirección es information que
vale la pena preservar como hecho inmutable, no solo un valor a corregir.

### Lo que costó: `Aporte` no tenía identidad

`Aporte` era un `record(ReferenciaUsuario usuario, BigDecimal monto, LocalDate fecha)`
sin ninguna clave propia -- nada permitía decir "este aporte, no el otro".
Ganó un campo `id` (`UUID.randomUUID().toString()`, generado en el servicio
al aportar, igual que `Ingreso`).

`AporteRequest.monto` pasó de aceptar cualquier signo a `@Positive`: ya no
existe la corrección por signo, así que un aporte siempre es "puse esta
plata", nunca "saqué esta plata". El chequeo manual de cero que vivía en el
servicio se borró -- Bean Validation ya cubre cero y negativo en un solo
lugar, mismo precedente que `RegistrarIngresoRequest` en la 2.3c.

### Dos bugs reales, encontrados contra Mongo de verdad y no con mocks

Los dos métodos nuevos de `PozoConsultasImpl` (`editarAporte`,
`borrarAporte`) tocan un elemento DENTRO de un array embebido, filtrando
además por DUEÑO (`usuario.usuarioId`) para que nadie edite o borre el
aporte de otra persona. Escribir ese filtro mal es fácil, y un mock
programado con `thenReturn(true)` nunca lo iba a notar -- los dos bugs de
abajo solo aparecieron pegándole a un backend real con `curl` y dos
usuarios distintos, el mismo motivo por el que este proyecto tiene un smoke
test aparte de los tests con mocks.

**Bug 1 -- dos condiciones sueltas sobre un array no exigen el mismo
elemento.** La primera versión de `editarAporte` filtraba con
`"aportes.id".is(aporteId).and("aportes.usuario.usuarioId").is(usuarioId)`,
dos condiciones unidas con `.and()` pero SIN `$elemMatch`. En Mongo, dos
condiciones así sobre un array se evalúan cada una por separado contra
CUALQUIER elemento -- no exigen que las cumpla el mismo. Si Viole tiene su
propio aporte en el mismo pozo, pedir editar el aporte de Franco (pasando
el id del aporte de Franco pero el usuarioId de Viole) podía matchear el
documento igual: el id lo satisface el aporte de Franco, el usuarioId lo
satisface el de Viole, y el `$` posicional de la actualización queda
apuntando a un elemento ambiguo. Se encontró leyendo el código después del
bug 2 (abajo), antes de que un test lo expusiera -- la primera ronda de
prueba usó un atacante sin aportes propios en el pozo, que es justo el caso
que no alcanza para disparar este bug.

**Bug 2 -- el `$inc` de `version` no es condicional al `$pull`, y esto SÍ
se vio fallar en vivo.** `borrarAporte` encadenaba `.pull(...)` (con el
chequeo de dueño solo adentro del propio filtro del pull) junto con
`.inc("version", 1)` en el mismo `Update`. El `$inc` se aplica siempre que
el filtro de ARRIBA matchee el documento, sin importar si el `$pull`
efectivamente sacó algo del array. Con un filtro de arriba que solo pedía
`_id` + `grupo_id` (sin dueño), pedir borrar el aporte de Franco siendo
Viole devolvía **HTTP 200**: el documento matcheaba, `version` subía,
`modifiedCount` daba mayor a cero, sin haber borrado nada. Confirmado en
vivo: B borrando el aporte de A daba 200 en vez de 404, y una lectura
posterior mostraba el aporte de A intacto.

**La solución a los dos es la misma:** mover el chequeo de dueño al filtro
de ARRIBA con `$elemMatch` (`"aportes"` con un `elemMatch` que exige `id` Y
`usuario.usuarioId` del MISMO elemento). Así ni el `$pull` ni el `$inc` se
ejecutan si el aporte no existe o no es de quien lo pide: el documento
entero deja de matchear, y `modifiedCount` vuelve a significar lo que el
servicio necesita que signifique.

**Verificado con el caso que de verdad expone el bug 1**: dos usuarios A y
B, cada uno con su propio aporte en el mismo pozo. B editando o borrando el
aporte de A → 404 en los dos casos, nada cambia. A editando el propio →
200, con `sobrantePorPersona` recalculado bien en la respuesta. B borrando
el propio → 200. Mismo patrón que ya usa `agregarAporte` y el resto de este
archivo: el filtro de Mongo es la única fuente de verdad sobre "a quién
pertenece esto", y el servicio no necesita (ni puede) distinguir "no
existe" de "no es tuyo" -- mismo criterio que `noSeDistingueElMotivo` ya
prueba para `agregarAporte` y `cerrar`.

### En mobile

`app/vaquita.tsx`: la fila del historial de aportes se comporta igual que
la de "Mi Plata" (`mi-plata.tsx`) -- tocarla abre un `ActionSheetIOS` con
Editar/Borrar/Cancelar. Editar convierte la fila en un input inline con
Guardar/Cancelar al lado, y el formulario de "Tu aporte" de arriba se
oculta mientras tanto, mismo criterio que ya usa Mi Plata para no mostrar
dos inputs de monto a la vez sin motivo.

Verificado: 167 tests en el backend, todos en verde; `tsc --noEmit
--noUnusedLocals` y `expo export --platform ios` en mobile, limpios. El
flujo completo (typo corregido con edición, aporte duplicado borrado, y las
cuatro combinaciones de dueño cruzado en 404) se probó a mano con `curl`
contra Mongo real, y de ahí salieron los dos bugs de arriba. La misma
sección se sumó a `scripts/smoke-test.ps1`, pero **sin correr el script
todavía** -- esta sandbox no tiene PowerShell. No hubo teléfono esta
vuelta tampoco, así que falta confirmar que el `ActionSheetIOS` se sienta
igual de natural acá que en Mi Plata.
