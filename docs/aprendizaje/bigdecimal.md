# `BigDecimal` y el dinero en Java

## Por qué `double` no sirve para plata

`double` y `float` son punto flotante **binario**. Representan números como
sumas de potencias de dos, y hay decimales que en base 2 no tienen
representación finita — igual que 1/3 no la tiene en base 10.

`0.1` es uno de esos. Lo que realmente se guarda en un `double` cuando escribís
`0.1` es:

```
0.1000000000000000055511151231257827021181583404541015625
```

De ahí sale el clásico:

```java
System.out.println(0.1 + 0.2);   // 0.30000000000000004
```

Con plata eso es inaceptable. No porque un centavo importe en una operación,
sino porque el error **se acumula**: sumás mil gastos y el total no cierra con
la suma manual. Y en un sistema de saldos, un total que no cierra es un sistema
en el que nadie confía.

> Regla sin excepciones: **`double` y `float` nunca tocan dinero.**

## Qué es `BigDecimal`

Un número decimal exacto, guardado como dos cosas:

- un **valor sin escala** (un entero grande)
- una **escala**: cuántos dígitos van después de la coma

`10.01` se guarda como el entero `1001` con escala `2`. Es decimal exacto, no
una aproximación binaria.

La **precisión** es la cantidad total de dígitos significativos. Cuando en la
entidad escribimos:

```java
@Column(nullable = false, precision = 12, scale = 2)
private BigDecimal monto;
```

le estamos diciendo a Hibernate que genere `NUMERIC(12,2)` en Postgres: 12
dígitos en total, 2 después de la coma, o sea hasta 9.999.999.999,99.

## La trampa del constructor

Esta es la que más se ve en código ajeno y hay que saber detectarla:

```java
// MAL: el double ya viene con la basura binaria, y BigDecimal la copia fielmente
new BigDecimal(0.1);
// 0.1000000000000000055511151231257827021181583404541015625

// BIEN: el String nunca pasa por double
new BigDecimal("0.1");     // 0.1

// ACEPTABLE: valueOf usa Double.toString() por debajo, que redondea a "0.1"
BigDecimal.valueOf(0.1);   // 0.1
```

Usar `new BigDecimal(double)` anula el motivo por el que elegiste `BigDecimal`.
**Siempre construir desde `String`**, o desde `valueOf` si el valor ya viene
como `double` de algún lado.

## Es inmutable

Todas las operaciones **devuelven un objeto nuevo**; ninguna modifica el
original. El error clásico:

```java
BigDecimal total = new BigDecimal("100.00");

total.add(new BigDecimal("50.00"));          // no hace nada, el resultado se tira
total = total.add(new BigDecimal("50.00"));  // así sí
```

Compila y no avisa nada. Si un total te da mal, este es el primer lugar donde
mirar.

La aritmética es por métodos, porque Java no tiene sobrecarga de operadores:

```java
monto.add(otro)         // +
monto.subtract(otro)    // -
monto.multiply(otro)    // *
monto.divide(otro, ...) // /  (ver abajo)
monto.negate()          // -x
```

## La división obliga a decidir el redondeo

Acá es donde `BigDecimal` te fuerza a ser explícito, y es una virtud, no una
molestia:

```java
new BigDecimal("10.01").divide(new BigDecimal("2"));
// 5.005  -> anda, pero te quedan 3 decimales cuando querías 2

BigDecimal.ONE.divide(new BigDecimal("3"));
// ArithmeticException: Non-terminating decimal expansion;
// no exact representable decimal result.

new BigDecimal("10.01").divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
// 5.01
```

Si el resultado no es exacto y no dijiste cómo redondear, **explota**. El
lenguaje no te deja ser ambiguo con la plata.

`RoundingMode` más usados:

- **`HALF_UP`** — el redondeo del colegio: 5.005 → 5.01. Intuitivo.
- **`HALF_EVEN`** — redondeo bancario: en el empate va al par más cercano
  (5.005 → 5.00, pero 5.015 → 5.02). Se usa en finanzas porque `HALF_UP`
  siempre empuja para arriba y, sobre miles de operaciones, sesga el total.

## La técnica que usamos en este proyecto

El problema real: un gasto de **$10,01 al 50/50** son $5,005 por persona.
Alguien se come el centavo, y hay que decidir quién sin que las partes dejen de
sumar el total.

**Nunca dividas dos veces.** Calculás una parte con división y redondeo, y la
otra la sacás por resta:

```java
BigDecimal monto   = new BigDecimal("10.01");
BigDecimal parteA  = monto.divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP); // 5.01
BigDecimal parteB  = monto.subtract(parteA);                                     // 5.00
// parteA + parteB == 10.01, exacto, siempre
```

Si dividieras las dos partes por separado te podrían dar 5.01 y 5.01, que suman
10.02 — un centavo salido de la nada. Con la resta eso es imposible por
construcción.

Esto es exactamente lo que hace nuestro modelo: `Gasto` guarda `monto` y
`montoPagador`, y la deuda del otro es `monto.subtract(montoPagador)`. La
división ocurre una sola vez, al crear el gasto, y el resultado queda congelado
en la fila. Ninguna lectura del saldo vuelve a dividir.

## `equals()` vs `compareTo()`

La trampa que genera tests que pasan cuando deberían fallar (y al revés):

```java
new BigDecimal("2.0").equals(new BigDecimal("2.00"));         // false
new BigDecimal("2.0").compareTo(new BigDecimal("2.00")) == 0; // true
```

`equals()` compara **valor y escala**. `2.0` tiene escala 1 y `2.00` escala 2,
así que para `equals` son objetos distintos aunque valgan lo mismo.

> **Para comparar valor, siempre `compareTo()`.**

Corolario: nunca uses `BigDecimal` como clave de un `HashMap` ni lo metas en un
`HashSet` esperando deduplicación, porque `hashCode()` es consistente con
`equals()` y también depende de la escala.

Para forzar una escala concreta:

```java
monto.setScale(2, RoundingMode.HALF_UP);
```

## Lo que tengo que poder explicar

- Por qué `0.1 + 0.2` no da `0.3` en `double`, y por qué eso descalifica al tipo
  para manejar dinero.
- Qué son la precisión y la escala de un `BigDecimal`, y cómo se traducen a
  `NUMERIC(12,2)` en Postgres.
- Por qué `new BigDecimal(0.1)` está mal y `new BigDecimal("0.1")` está bien.
- Por qué `total.add(x)` sin asignar no hace nada.
- Por qué `divide()` puede lanzar `ArithmeticException` y qué le tengo que pasar
  para que no lo haga.
- La diferencia entre `HALF_UP` y `HALF_EVEN`, y por qué en finanzas se prefiere
  el segundo.
- Por qué `equals()` dice que `2.0` y `2.00` son distintos, y qué usar en su
  lugar.
- Por qué al repartir un monto conviene dividir una sola vez y sacar la otra
  parte por resta.

## Lo que todavía no sé

- **`MathContext`** — otra forma de controlar precisión y redondeo, a nivel de
  dígitos significativos en vez de decimales fijos.
- **Formateo y localización** — mostrar `$1.234,56` con separadores argentinos.
  Va a aparecer en la app mobile, y se resuelve del lado del cliente, no en el
  backend.
- **Monedas y conversión** — está fuera de alcance del MVP, pero el patrón
  estándar es guardar monto + código de moneda juntos y nunca sumar montos de
  monedas distintas.
- **`long` de centavos** — la alternativa que descartamos. Vale entender cuándo
  se elige: sistemas de altísimo volumen donde el costo de `BigDecimal` (que es
  un objeto, no un primitivo) se nota.
