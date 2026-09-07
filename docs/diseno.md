# Diseño y estructura de MiNutria

Este archivo es la fuente de verdad de dos cosas: **los colores y tipografías**
que comparten la app mobile, la web y cualquier mockup, y **cómo se organizan
las carpetas** en cada stack.

Las decisiones de producto (qué hace la app y por qué) viven en
`docs/entrevista-usuaria.md` y en `CLAUDE.md`. Acá está el cómo se ve y dónde va
cada archivo.

---

## 1. Los colores

Salieron del proyecto de Lovable y están en **oklch**, no en hex, a propósito.

oklch es `lightness / chroma / hue`. Su gracia es que la luminosidad es
perceptual: dos colores con la misma L se ven igual de claros aunque tengan
tonos distintos. En hex eso no pasa — `#0000FF` y `#FFFF00` tienen valores
parecidos y uno es casi negro a la vista y el otro casi blanco. Con oklch podés
mover el `hue` sin que la interfaz se te desbalancee, que es exactamente lo que
hace falta cuando hay que ajustar una paleta.

| Token | Valor | Para qué |
|---|---|---|
| `--background` | `oklch(0.965 0.018 92)` | Crema de fondo |
| `--foreground` | `oklch(0.36 0.045 60)` | Texto, marrón cálido |
| `--card` | `oklch(0.995 0.008 95)` | Superficies elevadas |
| `--muted-foreground` | `oklch(0.58 0.035 62)` | Texto secundario |
| `--border` | `oklch(0.9 0.025 85)` | Líneas y divisores |
| `--sand` | `oklch(0.93 0.035 88)` | Fondos suaves, pistas de barras |
| `--bark` | `oklch(0.42 0.055 58)` | Marrón nutria, base de las sombras |
| `--river` | `oklch(0.62 0.085 200)` | Teal: lo compartido, lo tranquilo |
| `--river-deep` | `oklch(0.5 0.08 205)` | Teal para texto sobre claro |
| `--river-soft` | `oklch(0.88 0.045 195)` | Fondo de pastillas teal |
| `--hormiga` | `oklch(0.72 0.13 75)` | **Ámbar: el gasto evitable** |
| `--hormiga-soft` | `oklch(0.93 0.05 85)` | Fondo de la pastilla hormiga |
| `--terracotta` | `oklch(0.61 0.115 42)` | La acción principal |
| `--terracotta-deep` | `oklch(0.49 0.095 42)` | Presionado |
| `--leaf` | `oklch(0.58 0.09 165)` | Buenas noticias |
| `--note` | `oklch(0.925 0.028 160)` | Fondo de avisos suaves |

### La regla que no se rompe

**El ámbar es del gasto hormiga y de nada más.** Es la feature central de la
app: si el ámbar también decora botones, badges y encabezados, deja de querer
decir algo. Cuando Viole ve ámbar en cualquier pantalla, tiene que ser plata que
podría no haber gastado.

Lo mismo con el teal: es lo compartido. Y la terracota es la acción principal —
un solo botón terracota por pantalla.

### Por qué no hay tema oscuro

La identidad es una nutria en un río de día. Un modo oscuro no es imposible,
pero sería una segunda paleta que mantener y no aporta nada a dos personas que
abren la app treinta segundos en un mostrador. Si algún día se hace, los tokens
ya están separados del uso y alcanza con redefinirlos.

## 2. Las tipografías

| Rol | Fuente | Dónde |
|---|---|---|
| Display | **Fraunces** | Títulos, montos, todo número protagonista |
| Cuerpo | **Nunito Sans** | Texto, botones, etiquetas |

Fraunces es una serif de alto contraste con eje óptico variable: en tamaño
grande se ve editorial, y es lo que hace que `$23.900,00` parezca una portada y
no una celda de Excel. Nunito Sans es redondeada y neutra, y no pelea.

Los montos van siempre con `tabular-nums`, para que los dígitos ocupen lo mismo
y las columnas de plata queden alineadas.

## 3. Las nutrias

Cuatro ilustraciones, una por estado más la de la pareja:

```
assets/nutrias/
  contenta.png      flotando de espaldas en el agua
  tranquila.png     sentada, manitos juntas
  preocupada.png    sentada, cejas caidas
  nosotros.png      las dos tomadas de la mano
```

Se guardan una sola vez en la raíz del repo y las consumen tanto `mobile/` como
`web/`. Son PNG con fondo transparente.

**El ánimo lo decide el backend**, no la app: `GET /gastos/resumen` devuelve
`animo` con `CONTENTA`, `TRANQUILA` o `PREOCUPADA`, y el cliente solo elige qué
archivo mostrar. Así las dos plataformas muestran la misma nutria y los umbrales
se ajustan sin redeployar nada.

---

## 4. La estructura de carpetas

### El backend ya es MVC, y por eso no se toca

`controlador / servicio / repositorio / modelo` es arquitectura por capas, que es
lo que MVC significa en un backend sin vistas: el controlador recibe y delega, el
modelo son las entidades, y la "vista" son los DTO que se serializan a JSON.

Vale saber la diferencia para una entrevista: **el MVC clásico tiene tres
piezas; acá hay cuatro**, porque se agrega el repositorio para que el servicio no
sepa de SQL. Y la vista está invertida — no renderiza HTML, devuelve datos que
otro renderiza.

### En React y React Native, MVC no sirve

Y conviene poder explicar por qué, porque es una pregunta común.

MVC organiza **por tipo de archivo**: todos los modelos juntos, todos los
controladores juntos. Eso funciona cuando el framework impone esa separación
(Rails, Spring). En React no existen esas tres capas: un componente tiene estado,
lógica y presentación en el mismo archivo, y eso es deliberado.

El costo concreto de forzarlo: para tocar "cargar gasto" tendrías que abrir
`models/gasto.ts`, `controllers/gastoController.ts` y `views/GastoForm.tsx`. Tres
carpetas para un cambio. **La unidad de cambio en React es la feature, no la
capa.**

Por eso va organización por features:

```
mobile/
  app/                        rutas de expo-router: cada archivo es una pantalla
    _layout.tsx
    login.tsx
    (tabs)/
      resumen.tsx
      nosotros.tsx
    gasto/nuevo.tsx
  src/
    api/
      cliente.ts              fetch con el Bearer y el manejo de errores
      tipos.ts                los DTO del backend, en TypeScript
    features/
      auth/                   login, registro, sesion
      gastos/                 alta, edicion, listado
      resumen/                totales y animo
      saldo/                  quien le debe a quien
    componentes/              Boton, Chip, Switch, Nutria, Monto
    tema/
      colores.ts              los tokens de arriba
      tipografia.ts
    almacenamiento/
      sesion.ts               expo-secure-store, NUNCA AsyncStorage
  assets/nutrias/             -> symlink o copia de /assets/nutrias
```

Cada feature adentro:

```
features/gastos/
  pantallas/          lo que ve el usuario
  componentes/        piezas que solo usa esta feature
  hooks/              useGastos, useCrearGasto: el estado y las llamadas
  api.ts              las llamadas a /gastos
```

Si querés el paralelo con MVC para explicarlo en una entrevista:

| MVC | Equivalente en la app |
|---|---|
| Modelo | `api/tipos.ts` + `features/*/api.ts` |
| Vista | `pantallas/` y `componentes/` |
| Controlador | `hooks/` |

La diferencia es que están agrupados por feature y no por capa. **Un cambio en
"cargar gasto" toca una sola carpeta**, que es el punto.

### Por qué `componentes/` está afuera de `features/`

Un `Boton` no le pertenece a ninguna feature. La regla práctica: si lo usan dos
features, sube a `componentes/`; si lo usa una sola, se queda adentro. Empezar
poniendo todo en compartido termina en una carpeta de cincuenta componentes donde
nadie sabe cuáles se usan.

### Lo que NO va a haber

- **Redux o Zustand.** El estado del servidor lo maneja el fetch de cada
  pantalla; el estado global es solo el token, y para eso alcanza un Context.
- **Una carpeta `utils/`.** Se convierte en el cajón de todo. Cada helper vive
  al lado de lo que lo usa.
- **Barrel files (`index.ts` que reexporta todo).** Rompen el tree-shaking y
  esconden de dónde viene cada cosa.

---

## 5. El mockup

Hay un mockup navegable con las tres pantallas, el reparto personalizado y las
categorías que se agregan al vuelo. Sirve de referencia visual para la sesión 6:
cada control está mapeado contra el endpoint que lo alimenta.
