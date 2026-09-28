# Mandar MiNutria a App Review

Todo lo que va en App Store Connect, listo para copiar y pegar, y los pasos en
orden. Los textos respetan los límites de cada campo (verificados contando
caracteres, no a ojo).

> **La regla que guía todos los textos:** describir lo que obtiene **una cuenta
> nueva**. Desde el registro abierto de la v1.0, quien baja la app arranca sola
> en su grupo, y la sección de pareja (compartidos, saldo, vaquita) no le
> aparece. Si la ficha promete algo que el revisor no puede ver, es un rechazo
> por guideline 2.3 (metadata que no coincide con la app).

---

## 0. Pasos, en orden

1. [x] **El ping de Render está vivo.** Si el revisor abre la app con el servicio
   dormido, espera 40-60 segundos y puede rechazarla por "no responde". Ver
   `docs/deploy.md`, paso 5. *Verificado el 28/9: después de 22 minutos sin
   tráfico propio, `/actuator/health` respondió en 0,6 s. Render duerme a los
   15, así que el cron externo está pegando.* Conviene volver a mirarlo el día
   que se manda.
2. [ ] **Build en TestFlight** con todo lo de la v1.0 (registro abierto, borrado
   de cuenta, ícono, splash, descripción opcional, sin iPad). **El build ya está
   hecho**: build 3 (versión 1.0.0, commit `7c0dcb4`), id de EAS
   `cae27788-782b-40bd-b0de-ed85b1f2167b`. Falta subirlo: necesita el
   **Apple ID numérico de la app**
   (`ascAppId`), que está en App Store Connect → la app → Información de la app
   → "Apple ID". Dos formas:

   ```
   cd mobile; eas submit --platform ios --id cae27788-782b-40bd-b0de-ed85b1f2167b
   ```

   (interactivo: pregunta lo que falte), o dejarlo fijo en `mobile/eas.json`
   para que `eas build --auto-submit` suba solo la próxima vez:

   ```json
   "submit": { "production": { "ios": { "ascAppId": "1234567890" } } }
   ```

   Después de subirlo, Apple lo procesa (10-30 minutos) y aparece en TestFlight.
3. [ ] **Recorrido final en el teléfono** con ese build (sección 1).
4. [ ] **Cuenta demo**: `.\scripts\crear-cuenta-demo.ps1`, **el mismo día que
   mandás a revisión** (los gastos se fechan relativos a hoy).
5. [ ] **Capturas**: con la cuenta demo, sacar 4 o 5 en el teléfono, copiarlas a
   `capturas\` y correr `.\scripts\capturas-app-store.ps1` (sección 3).
6. [ ] **Corregir la política de privacidad en Notion**: dice que la contraseña
   se guarda "cifrada (con BCrypt)". BCrypt es un hash, no un cifrado: lo
   cifrado se puede descifrar, un hash no. Poner "hasheada" o "guardada con un
   hash irreversible".
7. [ ] **Completar App Store Connect** con las secciones 2, 4, 5 y 6.
8. [ ] **Elegir el build** en la versión 1.0 y **Add for Review**.

---

## 1. Recorrido final en el teléfono (con el build nuevo)

Lo que la v1.0 cambió y todavía no se vio en un teléfono:

- [ ] El **ícono** en la pantalla de inicio: la nutria de borde a borde, sin
      blanco en las esquinas.
- [ ] La **splash**: la nutria contenta sobre el crema, sin blanco antes ni una
      ruedita después.
- [ ] **Registrarse** con un email nuevo: entra directo, y el resumen **no**
      muestra "Gastos compartidos" ni "La vaquita del viaje"; el alta no ofrece
      compartido.
- [ ] Cargar un gasto **sin descripción**: se guarda, y en la lista aparece con
      el nombre de la categoría.
- [ ] Las **tildes**: "Cerrar sesión", "Quién le debe a quién", el aviso de
      gastos sin enviar dice "señal".
- [ ] **Ajustes → Política de privacidad** abre Notion.
- [ ] **Ajustes → Borrar mi cuenta** con la cuenta de prueba: pide la
      contraseña, avisa si hay gastos sin mandar, y al borrar vuelve al login.
      Después, esa cuenta ya no entra.
- [ ] Con la cuenta de **Viole o Franco**: la sección de pareja sigue estando.

---

## 2. La ficha

| Campo | Valor |
|---|---|
| Idioma principal | Español (México): es el que ven los usuarios de Latinoamérica |
| Nombre (30) | `MiNutria` — si estuviera tomado: `MiNutria: gastos hormiga` |
| Subtítulo (30) | `Tus gastos hormiga, a la vista` |
| Categoría principal | Finanzas |
| Categoría secundaria | Estilo de vida |
| Precio | Gratis |
| Disponibilidad | Argentina (la copy es rioplatense y los montos, en pesos) |
| Copyright | `2026 <tu nombre completo>` |
| URL de soporte | https://github.com/franpipito/shared-expenses-app/blob/main/docs/soporte.md |
| URL de privacidad | https://app.notion.com/p/Pol-tica-de-privacidad-MiNutria-3e52ae33cd65802c9d72cc8b9725914f |
| URL de marketing | (opcional, vacío) |

### Texto promocional (170)

```
Anotá un gasto en segundos, marcá los que podías evitar y mirá cuánto suman en el mes. La nutria te cuenta cómo venís, sin retos.
```

### Descripción (4000)

```
MiNutria es un registro de gastos pensado para anotar parado en el mostrador, en segundos.

Cada gasto lleva categoría y monto, una descripción si querés, y un solo interruptor: ¿fue un gasto hormiga? Un gasto hormiga es el que, mirado en frío, podrías no haber hecho: el café de todos los días, el delivery por fiaca, el uber que podía ser colectivo. El mismo uber puede ser necesario un día y evitable otro, así que lo decidís vos, en el momento.

LO QUE VES
• El total hormiga del mes, grande y primero: es el número que importa.
• La nutria, que cambia de ánimo según cómo venís contra el mismo tramo del mes pasado: contenta si gastaste menos en cosas evitables, preocupada si se te fue la mano. Nunca te reta, y no depende de cuánto ganás: te compara con vos.
• La lista del mes, con la marca de hormiga en cada gasto, y el total por categoría.
• Los meses anteriores, con el selector de mes.

RÁPIDA, AUNQUE NO HAYA SEÑAL
Guardar un gasto no espera a internet: queda en el teléfono y se manda solo cuando vuelve la señal, sin duplicarse.

SIN TOPES QUE CUMPLIR
Si tus ingresos cambian mes a mes, un presupuesto fijo no sirve. La idea es más simple: ver lo evitable y bajarlo.

PRIVADA
No hay publicidad, no se venden datos y no se conecta a tu banco ni a ninguna billetera: todo lo que ves lo cargaste vos. Podés borrar tu cuenta y tus datos desde Ajustes cuando quieras.
```

### Palabras clave (100)

```
gastos,hormiga,ahorro,finanzas,plata,registro,control de gastos,billetera,cuentas,nutria
```

---

## 3. Capturas

- **Slot obligatorio: iPhone 6,9"**, a 1290 × 2796. Con eso Apple escala para
  los demás tamaños de iPhone. **No hacen falta capturas de iPad**: la app ya no
  declara soporte para iPad (`supportsTablet: false`).
- Sacarlas **con la cuenta demo**, así no aparecen gastos reales.
- Cuáles, en este orden (la primera es la que más se ve en la tienda):
  1. El **resumen** con la nutria y el total hormiga.
  2. La **lista del mes**, donde se ven los dos uber de $4.000, uno marcado como
     hormiga y el otro no: es literalmente el producto.
  3. El **alta** de un gasto con el interruptor de hormiga prendido.
  4. El resumen **por categoría** (scrolleando el resumen).
  5. (Opcional) **Ajustes**.
- En el teléfono: botón lateral + subir volumen. Copiarlas a `capturas\` en la
  raíz del repo y correr:

  ```
  .\scripts\capturas-app-store.ps1
  ```

  Quedan en `capturas\app-store\` a 1290 × 2796, sin canal alfa (App Store
  Connect las rechaza con transparencia). La carpeta está en `.gitignore`.

---

## 4. Privacidad de la app (App Privacy)

**¿Recolectás datos?** Sí. **¿Se usan para rastrear (tracking)?** No, ninguno.

| Tipo de dato (como lo nombra Apple) | Uso | ¿Vinculado a la persona? | ¿Tracking? |
|---|---|---|---|
| Información de contacto → **Nombre** | Funcionalidad de la app | Sí | No |
| Información de contacto → **Correo electrónico** | Funcionalidad de la app | Sí | No |
| Información financiera → **Otra información financiera** (los gastos: monto, categoría, descripción, fecha) | Funcionalidad de la app | Sí | No |
| Identificadores → **ID de usuario** (el id de la cuenta) | Funcionalidad de la app | Sí | No |

Lo que **no** se declara, porque la app no lo junta: ubicación, contactos,
historial de navegación, diagnóstico o crashes (no hay ningún SDK de analytics
ni de crash reporting), datos de uso, compras, salud.

---

## 5. Clasificación por edad

Responder **"Ninguno" / "No"** a todas las preguntas del cuestionario: no hay
violencia, contenido sexual, apuestas, compras dentro de la app, chat entre
usuarios, ni acceso libre a la web (el único link externo es la política de
privacidad, que abre el navegador del sistema). Resultado esperado: **4+**.

**Encriptación (Export Compliance):** ya está resuelta en `app.json`
(`ITSAppUsesNonExemptEncryption: false`: solo HTTPS), así que App Store Connect
no la pregunta en cada build.

---

## 6. App Review Information

**Sign-In Information**: marcar "Sign-in required".

- Usuario: `revision@minutria.app`
- Contraseña: la que elegiste al correr `crear-cuenta-demo.ps1`.

**Contact Information**: tu nombre, teléfono y email.

**Notes** (en inglés: es lo que lee el revisor):

```
MiNutria is a personal expense tracker, in Spanish (Argentina).

The demo account in Sign-In Information already has sample expenses for the current and the previous month, so the main screen shows the monthly total and the otter's mood.

- "Gasto hormiga" (literally "ant expense") is the Spanish term for small, avoidable expenses. Each expense has a switch to mark it as one, and the main number of the app is the monthly sum of those.
- The otter's mood is computed by the server, comparing avoidable spending against the same part of the previous month.
- Expenses saved without connection are queued on the device and sent automatically when the connection comes back.
- Account deletion: tap "Ajustes" at the top right of the main screen, then "Borrar mi cuenta". It asks for the password to confirm.
- A new account starts on its own. Sharing expenses with another person is not available to new accounts yet; it will come in a future version, so no partner-related screens are shown.
```

---

## 7. Después de mandar

- **No borrar nunca la cuenta demo** mientras la app esté publicada: Apple la usa
  en cada revisión de cada versión nueva.
- Si la revisión cae en un **mes nuevo**, el resumen de la cuenta demo arranca
  vacío. Volver a correr `crear-cuenta-demo.ps1` carga el mes en curso sin
  duplicar lo anterior.
- Si rechazan, el mensaje llega a App Store Connect → Resolution Center.
- **Pendiente para la 1.0.1**: `npx expo-doctor` marca cuatro paquetes con la
  versión de parche atrasada (`expo`, `expo-constants`, `expo-linking`,
  `expo-router`). No se actualizaron antes de la revisión a propósito: el build
  que se manda es el que se probó. Al actualizarlos, `npx expo install --check`,
  y **mirar el CI antes de buildear**: el npm 11 local puede podar del lock una
  dependencia opcional que el npm 10 de EAS espera (pasó en la v1.0).
