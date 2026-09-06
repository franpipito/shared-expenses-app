# JPA e Hibernate

## Las tres capas que se confunden todo el tiempo

Cuando la gente dice "JPA" o "Hibernate" suele mezclar tres cosas distintas:

| Capa | Qué es | Paquete |
|---|---|---|
| **JPA** (Jakarta Persistence) | La **especificación**. Interfaces y anotaciones, sin implementación | `jakarta.persistence.*` |
| **Hibernate** | Una **implementación** de esa especificación. Es quien realmente genera el SQL | `org.hibernate.*` |
| **Spring Data JPA** | Una capa encima que te genera los repositorios solo | `org.springframework.data.*` |

Esto no es trivia: se ve en el código. Casi todas nuestras anotaciones son
`jakarta.persistence` (estándar), pero una no:

```java
import org.hibernate.annotations.Check;   // <- esto es de Hibernate, no de JPA
```

Si algún día cambiaras Hibernate por otra implementación, esa línea se rompe y
el resto no. Es la diferencia entre programar contra la especificación y
programar contra el proveedor.

## La entidad mínima

```java
@Entity
@Table(name = "grupo")
public class Grupo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    protected Grupo() { }   // <- obligatorio, ver abajo
}
```

- **`@Entity`** — marca la clase como mapeable a una tabla. Hibernate la
  descubre al arrancar.
- **`@Table(name = "...")`** — opcional; sin ella usa el nombre de la clase.
  Conviene ponerla explícita para no depender de las reglas de nombrado.
- **`@Id`** — la clave primaria.
- **`@GeneratedValue`** — quién genera el valor.

### Sobre `GenerationType.IDENTITY`

Usa la columna `identity` de la base: es Postgres quien asigna el ID en el
INSERT.

El costo, que conviene saber: con `IDENTITY`, Hibernate **no puede agrupar
inserts en lote**, porque necesita el ID de vuelta inmediatamente después de
cada uno. Con `GenerationType.SEQUENCE`, Hibernate se reserva IDs de una
secuencia por adelantado y sí puede batchear.

Para dos personas cargando unos gastos por día da exactamente igual. En un
sistema con volumen de escritura, `SEQUENCE` es la elección estándar en Postgres.

### El constructor sin argumentos

JPA instancia las entidades **por reflexión** cuando lee filas de la base, y
para eso necesita un constructor sin argumentos. Si definís cualquier otro
constructor, Java deja de darte el implícito y tenés que escribirlo.

Lo declaramos `protected` y no `public` a propósito: Hibernate puede usarlo
igual, pero tu código no lo llama por accidente y se evita crear entidades a
medio construir.

Corolario: **una entidad JPA no puede ser un `record`**, ni tener campos
`final`. Hibernate necesita crear el objeto vacío y después llenarlo.

## Las relaciones y quién es el "dueño"

Este es el concepto que más cuesta al principio. En una relación bidireccional,
**uno de los dos lados es el dueño**, y el dueño es el que tiene la foreign key
en su tabla.

```java
// En Usuario: este lado es el DUEÑO. La FK grupo_id vive en la tabla usuario.
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "grupo_id", nullable = false)
private Grupo grupo;

// En Grupo: este lado es el INVERSO. Solo lectura.
@OneToMany(mappedBy = "grupo")
private List<Usuario> usuarios = new ArrayList<>();
```

`mappedBy = "grupo"` significa: *"esta relación ya está mapeada por el campo
`grupo` de la otra clase; no crees nada nuevo"*. Sin `mappedBy`, Hibernate
asumiría que son dos relaciones distintas y te inventaría una tabla intermedia.

Consecuencia práctica: **agregar un `Usuario` a `grupo.getUsuarios()` no persiste
nada.** Para que se guarde hay que setear `usuario.setGrupo(grupo)`, que es el
lado dueño.

## `LAZY` vs `EAGER`

Define **cuándo** se trae la entidad relacionada.

- **`EAGER`** — se trae siempre, junto con la entidad principal, con un JOIN.
- **`LAZY`** — no se trae hasta que alguien llame al getter. Hibernate deja en
  su lugar un *proxy*: un objeto falso que sabe cómo ir a buscar el dato real.

Los defaults de JPA son una trampa clásica:

| Anotación | Default |
|---|---|
| `@ManyToOne` | **EAGER** |
| `@OneToOne` | **EAGER** |
| `@OneToMany` | LAZY |
| `@ManyToMany` | LAZY |

O sea que un `@ManyToOne` sin tocar dispara un JOIN en **toda** consulta, uses o
no la relación. Con tres `@ManyToOne` en `Gasto` (grupo, pagadoPor, categoria),
listar gastos traería tres JOINs siempre.

Por eso en todas las nuestras está explícito:

```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
```

> Regla práctica: **`@ManyToOne` siempre LAZY**, salvo que tengas una razón
> concreta para lo contrario.

## El ciclo de vida de una entidad

Acá está el núcleo conceptual de JPA, y lo que explica casi todo el resto.

El **contexto de persistencia** (`EntityManager`) es un espacio de trabajo,
normalmente atado a una transacción, que lleva registro de las entidades que
está manejando. Una entidad pasa por cuatro estados:

| Estado | Qué significa |
|---|---|
| **Transient** | Creada con `new`. Hibernate no la conoce. Sin ID |
| **Managed** | Está dentro de un contexto abierto. Hibernate la vigila |
| **Detached** | Estuvo managed, pero el contexto se cerró |
| **Removed** | Marcada para borrar |

### Dirty checking: la parte que sorprende viniendo de SQL

Mientras una entidad está **managed**, Hibernate guarda una foto de cómo estaba
al cargarla. Al cerrar la transacción compara la foto contra el estado actual y
**emite el UPDATE solo**.

```java
@Transactional
public void cambiarDescripcion(Long id, String nueva) {
    Gasto gasto = repo.findById(id).orElseThrow();
    gasto.setDescripcion(nueva);
    // NO hace falta llamar a save(). Al cerrar la transacción, Hibernate
    // detecta el cambio y manda el UPDATE.
}
```

Viniendo de Express con SQL a mano esto es desconcertante: modificás un objeto
en memoria y la base cambia. Es la funcionalidad más potente de JPA y también
la que produce updates fantasma cuando no la entendés.

### `LazyInitializationException`

Si tocás una relación `LAZY` cuando la entidad ya está **detached**, el proxy no
tiene contexto al que pedirle el dato y explota:

```
org.hibernate.LazyInitializationException:
could not initialize proxy - no Session
```

**Vas a ver este error en la sesión 2**, casi seguro. No es un bug: es el diseño
avisándote de que estás pidiendo datos fuera de la transacción.

Las soluciones correctas son traer el dato dentro de la transacción: un `JOIN
FETCH` en la consulta, un `@EntityGraph`, o mapear a un DTO en el servicio.

### `open-in-view`

Spring Boot por defecto trae `spring.jpa.open-in-view=true`, que **mantiene el
contexto abierto durante toda la request**, incluida la serialización de la
respuesta. Con eso, los lazy loads en el controlador "funcionan".

El problema es que funcionan disparando consultas invisibles: serializás una
lista de 50 gastos, cada uno toca su categoría, y salen 51 queries sin que
aparezca nada raro en tu código. Es el problema **N+1**.

Nosotros lo apagamos:

```properties
spring.jpa.open-in-view=false
```

Así el error aparece de entrada, fuerte y claro, en vez de esconderse como un
problema de performance que descubrís en producción.

## `@Enumerated`: STRING vs ORDINAL

```java
@Enumerated(EnumType.STRING)
@Column(nullable = false, length = 20)
private TipoGasto tipo;
```

El default de JPA es **`ORDINAL`**, que guarda la *posición* de la constante en
el enum: `0`, `1`, `2`.

Por qué es peligroso: si mañana alguien reordena el enum o mete una constante en
el medio, **todos los datos históricos cambian de significado en silencio**. Un
gasto que era `PERSONAL` pasa a ser `COMPARTIDO` sin que nadie haya tocado una
fila.

`STRING` guarda el texto `'PERSONAL'`. Ocupa más y renombrar una constante
requiere migración, pero no se rompe solo.

> **`EnumType.STRING`, siempre.**

De yapa, Hibernate genera un CHECK con los valores válidos:

```sql
tipo varchar(20) not null check (tipo in ('PERSONAL','COMPARTIDO'))
```

## `@Version`: bloqueo optimista

```java
@Version
private Long version;
```

Hibernate agrega la versión al WHERE de cada UPDATE y la incrementa:

```sql
UPDATE gasto SET descripcion = ?, version = 4 WHERE id = ? AND version = 3
```

Si otro ya guardó, la versión en la base es 4, el `WHERE` no matchea, **el UPDATE
afecta 0 filas** y Hibernate lanza una excepción en vez de pisar el cambio ajeno.

Es exactamente el patrón de `UPDATE ... WHERE` condicional, pero automático en
cada escritura.

Se llama **optimista** porque asume que los conflictos son raros: no bloquea
nada, solo detecta el choque al final. Lo opuesto sería el bloqueo pesimista
(`SELECT ... FOR UPDATE`), que toma un lock en la base y hace esperar al otro.
Para dos personas que casi nunca editan el mismo gasto, el optimista es el
adecuado: cuesta una columna y cero contención.

## Callbacks del ciclo de vida

```java
@PrePersist
void alCrear() {
    this.creadoEn = Instant.now();
    this.actualizadoEn = this.creadoEn;
}

@PreUpdate
void alActualizar() {
    this.actualizadoEn = Instant.now();
}
```

Métodos que Hibernate invoca en momentos precisos: `@PrePersist` justo antes del
INSERT, `@PreUpdate` antes de cada UPDATE. También existen `@PostPersist`,
`@PostLoad`, `@PreRemove` y otros.

Sirven para campos derivados como las marcas de tiempo, sin ensuciar cada
servicio con `setActualizadoEn(...)`.

Detalle: `@PreUpdate` solo se dispara si Hibernate **detectó un cambio real** en
el dirty checking. Si no cambió nada, no hay UPDATE y no hay callback.

## Acceso por campo vs por getter

Hibernate decide cómo leer y escribir los datos según **dónde pusiste `@Id`**:

- `@Id` sobre el **campo** → acceso por campo (lee y escribe atributos directo)
- `@Id` sobre el **getter** → acceso por propiedad (usa getters y setters)

Nosotros lo pusimos sobre el campo, y por eso pudimos escribir:

```java
public boolean esHormiga() { return esHormiga; }
```

en lugar del convencional `isEsHormiga()`, sin que a Hibernate le importe.

**Ojo:** Jackson (la librería que serializa a JSON) **sí** usa la convención de
bean, y no detecta ese getter. No nos afecta porque vamos a serializar DTOs y no
entidades, pero es exactamente el tipo de detalle que muerde cuando lo olvidás.

## `ddl-auto`: cómodo y peligroso

```properties
spring.jpa.hibernate.ddl-auto=update
```

| Valor | Qué hace |
|---|---|
| `none` | Nada. Vos gestionás el esquema |
| `validate` | Solo verifica que la base coincida con las entidades. Falla si no |
| `update` | Agrega tablas y columnas faltantes |
| `create` | Borra todo y recrea. En cada arranque |
| `create-drop` | Igual, y además borra al apagar |

`update` es comodísimo para desarrollar, pero **nunca va a producción**:

- Nunca borra columnas ni tablas que sacaste del código.
- Nunca corrige un tipo de dato que cambió.
- No deja historial: no sabés qué se aplicó ni cuándo, ni podés volver atrás.
- No migra datos existentes.

Lo correcto en producción es **`validate` + una herramienta de migraciones**
(Flyway o Liquibase), donde cada cambio es un archivo SQL versionado en el repo.
En este proyecto eso llega en la sesión 5, antes del deploy.

## Lo que tengo que poder explicar

- La diferencia entre JPA, Hibernate y Spring Data JPA.
- Por qué una entidad necesita un constructor sin argumentos, y por qué no puede
  ser un `record`.
- Qué significa `mappedBy` y por qué agregar a la lista del lado inverso no
  persiste nada.
- Los defaults de fetch de cada tipo de relación, y por qué `@ManyToOne` EAGER
  es un problema.
- Los cuatro estados del ciclo de vida de una entidad.
- Qué es el dirty checking y por qué no hace falta llamar a `save()` sobre una
  entidad managed.
- Por qué aparece `LazyInitializationException`, qué la causa y cómo se resuelve
  bien.
- Qué hace `open-in-view=true` y por qué lo apagamos.
- Por qué `EnumType.ORDINAL` puede corromper datos históricos en silencio.
- Cómo funciona el bloqueo optimista con `@Version`, y en qué se diferencia del
  pesimista.
- Por qué `ddl-auto=update` no sirve para producción.

## Lo que todavía no sé

- **Consultas** — derivadas del nombre del método en Spring Data
  (`findByGrupoIdAndFechaBetween`), `@Query` con JPQL, y `JOIN FETCH`. Sesión 2.
- **`@Transactional`** — cómo Spring delimita las transacciones, propagación y
  por qué no funciona si te llamás a vos mismo dentro de la misma clase.
- **El problema N+1 en detalle** — cómo detectarlo y las herramientas para
  resolverlo (`@EntityGraph`, `JOIN FETCH`, batch size).
- **`cascade` y `orphanRemoval`** — propagar operaciones a las entidades hijas.
  No los usamos todavía.
- **`equals()` y `hashCode()` en entidades** — no se los pusimos. Es un tema con
  trampa, porque el ID es `null` hasta que se persiste y eso rompe el contrato
  de los `Set`.
- **Criteria API y Specifications** — construir consultas dinámicas con código
  en vez de strings. Aparece cuando los filtros se combinan de muchas formas.
- **Flyway** — migraciones de verdad. Sesión 5.
