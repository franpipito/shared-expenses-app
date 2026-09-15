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
  aportes: [ { usuarioId, nombre, monto, fecha } ],
  creado_en, actualizado_en
}
```

Los aportes van **embebidos**, por el mismo criterio que `ReferenciaUsuario`:
son pocos, están acotados (dos personas, un puñado de aportes por viaje) y
siempre se leen junto al pozo. Nunca se consultan solos.

El snapshot `{usuarioId, nombre}` sigue la misma regla que el resto del modelo, y
**el campo se llama `usuarioId` y no `id`** por el motivo documentado en
`ReferenciaUsuario`: Spring Data convierte cualquier propiedad llamada `id` a
ObjectId, también dentro de un documento embebido, y en silencio.

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

Nota: `updateFirst` con `$push` **no pasa por `@Version`**. Es correcto y es a
propósito: un aporte es un append, no una edición. El `@Version` del pozo
protege los cambios a sus campos propios (nombre, fechas, cierre).

## 6. Qué toca del código que ya funciona

Casi nada, y esa era la idea:

| Archivo | Cambio |
|---|---|
| `modelo/Gasto.java` | un campo `pozoId` nullable |
| `repositorio/GastoConsultasImpl.java:141` (`saldoDe`) | un `Criteria.where("pozo_id").is(null)` |
| `repositorio/GastoConsultasImpl.java:116` (`sumarHormigaDe`) | idem |
| `repositorio/GastoConsultasImpl.java:132` (`contarEn`) | idem |
| `servicio/GastoServicio.java:67` (`crear`) | validar el pozo y forzar `tipo = COMPARTIDO` |
| `dto/GuardarGastoRequest.java` | `String pozoId` opcional |

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
POST   /pozos/{id}/aportes     { monto }                               200
POST   /pozos/{id}/cerrar                                              200
```

```
PozoRespuesta {
  id, nombre, objetivo, estado, desde, hasta,
  aportado, gastado, restante,
  aportes: [ { usuarioId, nombre, monto, fecha } ]
}
```

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
  hormiga y de nada más (ver `docs/diseno.md`). Va teal, que es lo compartido.
- Es la segunda pantalla que se gana la ilustración de las dos nutrias
  (`nosotros.png`), que Viole pidió explícitamente al evaluar los mockups.
- La pantalla del viaje **no es mensual**. Es el primer agregado de la app que
  no se corta por mes, y está bien: el viaje trae su propio corte natural. Eso
  es justamente lo que le faltaba al saldo histórico para no ser un número que
  solo crece.

## 9. Lo que queda afuera a propósito

**El cierre del pozo con devolución del sobrante.** Es una liquidación de verdad,
con su flujo y su pantalla. Para el viaje no hace falta: vuelven, ven que
sobraron $120.000 y se lo transfieren. El campo `estado` queda en el documento;
la pantalla de cierre, no.

> **El pozo no necesita resolver la devolución para servir en el viaje.** Saber
> qué pedazo del problema no hay que resolver todavía es la mitad del trabajo.

Tampoco: múltiples pozos simultáneos, metas de ahorro, el pozo dentro del
resumen mensual, ni notificaciones de "queda poco".

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
