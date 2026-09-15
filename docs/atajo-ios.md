# Atajo de iOS para cargar un gasto

Un atajo de la app **Atajos** que hace `POST /gastos` directo contra la API, sin
abrir MiNutria. Se dispara con **Back Tap** (dos golpecitos en la parte de atrás
del iPhone) o con **"Oye Siri"**.

No requiere **ninguna línea de código** en la app: el endpoint ya existe y ya
acepta exactamente esto. Se arma una vez en cada teléfono y se borra al volver
del viaje.

> Contexto y por qué se eligió esto en vez de importar los gastos de Mercado
> Pago: ver la sección final de `docs/vaquita.md`.

---

## 1. Qué gana, y qué no

Conviene ser honesto, porque es lo que decide si vale la pena armarlo.

**No gana en el formulario.** El alta de MiNutria ya es monto con el teclado
abierto, un tap de categoría, descripción y guardar. Es difícil hacerlo más
corto.

**Gana en el arranque.** Back Tap contra: desbloquear → encontrar el ícono →
esperar que cargue el resumen → tocar el `+`. Con un backend que puede estar
arrancando en frío en Render, esa diferencia son varios segundos en el peor
momento posible, que es parada en el mostrador esperando el pedido.

**Y gana por voz.** *"Oye Siri, gasto del viaje"* con las manos ocupadas no
tiene equivalente en la app.

### La limitación que hay que conocer antes de armarlo

**El atajo no tiene cola offline y nunca la va a tener.** Es un POST y listo: si
no hay señal, falla y el gasto se pierde. La app sí puede guardar local y
sincronizar después.

O sea que conviven con roles distintos: **el atajo es el camino rápido cuando
hay señal, la app es el camino confiable.** Por eso el paso 8 del atajo (la
notificación de confirmación) no es decorativo.

## 2. Antes de empezar: juntar cuatro datos

Todo esto se hace una sola vez, desde la compu. Los comandos son para
**PowerShell 5.1**, así que van con `curl.exe` (el `curl` pelado es un alias de
`Invoke-WebRequest`) y con `;` para encadenar, nunca `&&`.

### a) La URL de la API

**Armá el atajo recién después de migrar a Render** (sesión 6.6). El atajo
apunta a una URL fija y la de Railway se muere con el crédito; si lo hacés
antes, lo hacés dos veces.

### b) El token

```powershell
$body = '{"email":"TU-EMAIL","password":"TU-PASSWORD"}'
curl.exe -s -X POST https://TU-API/auth/login -H "Content-Type: application/json" -d $body
```

Del JSON que vuelve, copiá el campo `token`. Dura **30 días**
(`app.jwt.duracion-dias`), así que para un viaje de cinco sobra.

### c) Los ids de las categorías

```powershell
curl.exe -s https://TU-API/categorias -H "Authorization: Bearer TU-TOKEN"
```

Son ObjectIds (`"68c1a2b3c4d5e6f7a8b9c0d1"`), no números. Anotá los seis: café,
uber, comida, ropa, regalos, otros.

### d) El id del pozo

```powershell
curl.exe -s https://TU-API/pozos/activo -H "Authorization: Bearer TU-TOKEN"
```

**Esto existe recién con la sesión 6.7.** Mientras tanto el atajo sirve igual:
omitís el campo `pozoId` y carga un `COMPARTIDO` normal, que es lo que la app
hace hoy.

## 3. El atajo, paso a paso

En la app **Atajos** → `+` → agregá estas acciones en orden:

| # | Acción | Configuración |
|---|---|---|
| 1 | **Elegir del menú** | Seis opciones: Café, Uber, Comida, Ropa, Regalos, Otros |
| 2 | **Texto** (dentro de cada rama del menú) | El `categoriaId` correspondiente |
| 3 | **Definir variable** | `categoria` ← el Texto del paso 2 |
| 4 | **Pedir entrada** | Tipo **Número**, mensaje "¿Cuánto?" → variable `monto` |
| 5 | **Pedir entrada** | Tipo **Texto**, mensaje "¿Qué fue?" → variable `desc` |
| 6 | **Formatear fecha** | Fecha actual, formato personalizado `yyyy-MM-dd` → variable `hoy` |
| 7 | **Obtener contenido de URL** | Ver abajo |
| 8 | **Mostrar notificación** | "Anotado ✓" |

### El paso 7 en detalle

- **URL**: `https://TU-API/gastos`
- **Método**: `POST`
- **Encabezados**:
  - `Authorization` → `Bearer TU-TOKEN`
  - `Content-Type` → `application/json`
- **Cuerpo de la solicitud**: `JSON`, con estos campos:

| Clave | Tipo | Valor |
|---|---|---|
| `monto` | Número | variable `monto` |
| `categoriaId` | Texto | variable `categoria` |
| `fecha` | Texto | variable `hoy` |
| `descripcion` | Texto | variable `desc` |
| `tipo` | Texto | `COMPARTIDO` |
| `pozoId` | Texto | el id del pozo *(omitir hasta la 6.7)* |
| `esHormiga` | Booleano | `false` |

El paso 8 importa: sin una confirmación visible no tenés forma de saber si se
guardó, y un POST fallido en Atajos es fácil de no ver.

## 4. Dónde engancharlo

**Back Tap** — es el bueno, y funciona en los iPhone 13 Pro:

> Ajustes → Accesibilidad → Tocar → **Tocar parte posterior** → Doble toque →
> elegí el atajo

**Siri** — la frase es el nombre del atajo. Si se llama "Gasto del viaje",
*"Oye Siri, gasto del viaje"*.

**Pantalla de inicio** — desde Atajos, compartir → "Añadir a pantalla de
inicio". Queda como una app más.

> **Ojo con el botón de Acción:** es del iPhone 15 Pro en adelante. En un 13 Pro
> no existe, así que los caminos son los tres de arriba.

## 5. El riesgo del token, y cómo se cierra

El JWT queda **en texto plano dentro de un atajo que iCloud sincroniza**. Para
dos personas y cinco días es un riesgo aceptable, pero aceptado a sabiendas y no
por no haberlo mirado.

Tres cosas que lo acotan:

1. **Un JWT no esconde nada, pero tampoco es una contraseña.** Da acceso a la
   cuenta hasta que expire; no permite cambiar la contraseña ni sacar el hash.
2. **Dura 30 días y se puede matar antes.** Al volver del viaje:
   `POST /auth/cerrar-sesiones` incrementa `token_version` y ese token deja de
   valer aunque le queden 25 días y la firma siga siendo válida.
3. **Borrá el atajo al volver.** Es una herramienta de cinco días.

El paso 2 es precisamente para lo que se construyó ese endpoint: es el botón de
"perdí el celular", y acá es el botón de "terminó el viaje".

## 6. Variantes que se evaluaron

**Dos atajos, uno normal y uno hormiga.** Se puede: duplicás el atajo y cambiás
`esHormiga` a `true`. No lo recomiendo para el viaje — los gastos del pozo son
plata que ya decidieron gastar, así que casi ninguno va a ser hormiga, y el caso
raro se carga desde la app.

**Un paso de menú para hormiga dentro del mismo atajo.** Agrega un tap a todas
las cargas para servir a una minoría. Va en contra de lo mismo que el atajo
viene a mejorar.

## 7. El escalón siguiente: App Intents

La versión nativa de esto son los **App Intents** de iOS: MiNutria expone
"anotar gasto" al sistema y aparece en Siri, en Spotlight y en un widget de la
pantalla de bloqueo, con la identidad de la app en vez de un atajo genérico. Y
sin token pegado en ningún lado, porque corre adentro de la app.

Es la solución correcta a largo plazo, y no es para este viaje: requiere código
Swift, o sea un config plugin de Expo y un dev client, con un build de EAS por
iteración. Es una sesión entera en territorio nuevo.

**El atajo da el 80% del beneficio a costo cero.** Queda anotado como candidato
a sesión propia después del viaje.
