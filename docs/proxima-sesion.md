# Próxima sesión: la v1.1 (antes del viaje)

La v1.0 está **en revisión de Apple** desde el 29/9/2026 (build 4). Este archivo
es el punto de partida de la sesión siguiente, pensada para correr **en la nube**
(claude.ai/code desde la notebook), no en la PC de Windows.

## Cómo arrancar

Abrí una sesión nueva sobre este repo y pegá:

> Leé `CLAUDE.md`, `docs/entrevista-usuaria.md` y `docs/proxima-sesion.md`.
> Arrancamos por la sección 1. Antes de programar cada feature de la sección 2,
> discutamos el diseño como pide CLAUDE.md.

**Lo que cambia en la nube** (no es la PC de Windows):

- **No hay Docker ni Mongo local**, ni PowerShell 5.1. Los tests del backend
  (`cd backend && ./mvnw test`) corren igual: todos usan mocks salvo
  `contextLoads`, que necesita Mongo y queda cubierto por el CI (levanta una
  Mongo de verdad). `scripts/smoke-test.ps1`, `crear-cuenta-demo.ps1` y
  `capturas-app-store.ps1` son de PowerShell: se corren en la PC.
- **EAS** necesita sesión: `eas login`, o la variable `EXPO_TOKEN` (un token de
  expo.dev → Account settings → Access tokens). Sin eso, los builds se lanzan
  desde la PC.
- **Las reglas de CLAUDE.md siguen**: nada contra la base sin permiso, y el git
  según lo que digas en esa sesión (en la de la v1.0 se autorizó a commitear y
  pushear cada feature testeada).
- **El CI es la red**: después de cada push, mirar que dé verde **antes** de
  buildear. En la v1.0 un `package-lock` roto rompió el build por no mirarlo.

---

## 1. Cerrar la v1.0 (primero, y lo más urgente arriba)

- [ ] **1 de octubre: volver a correr `.\scripts\crear-cuenta-demo.ps1`** (en la
      PC, con la misma contraseña). La revisión puede caer en octubre, y el
      resumen de la cuenta demo arrancaría vacío: el revisor vería una app sin
      datos. Correrlo de nuevo carga el mes en curso sin duplicar nada.
- [ ] **Probar el build 4 en TestFlight** con el recorrido de la sección 1 de
      `docs/app-store.md`. Lo nuevo que nadie vio en un teléfono: el reseteo de
      contraseña con el mail real, la vaquita con "Aportar", las categorías con
      tilde. Si algo está roto: retirar de revisión, arreglar, build 5.
- [ ] **Resultado de la revisión.** Si rechazan, el motivo está en App Store
      Connect → Resolution Center. Traerlo a la sesión.
- [ ] **Notion**: cambiar "cifrada (con BCrypt)" por *"La contraseña se guarda
      con un hash irreversible (BCrypt): ni siquiera quien administra la base de
      datos puede leerla."*
- [ ] **Render**: borrar la variable `CODIGO_INVITACION`, que nadie lee desde la
      v1.0.
- [ ] **Capturas nítidas para la 1.1**: las de la 1.0 pasaron por WhatsApp como
      foto y quedaron borrosas. Durante la revisión no se pueden cambiar; van con
      la próxima versión. Pasarlas por AirDrop, cable, o WhatsApp como documento.
- [ ] **Actualizar los parches de Expo** que marca `npx expo-doctor` (`expo`,
      `expo-constants`, `expo-linking`, `expo-router`) con
      `npx expo install --check`. Ojo: con el npm 11 local, mirar que el
      `package-lock` no pierda la entrada `expo/node_modules/react-native-worklets`
      (pasó en la v1.0; ver la sesión 6.11 en CLAUDE.md). El CI lo detecta.
- [ ] **Marcar la sesión 6.11 como hecha** en CLAUDE.md cuando la aprueben.

---

## 2. El alcance de la v1.1

Lo que se dejó afuera de la v1.0 a propósito ("ni balance, ni dólares, ni
gráficos, ni push, ni recurrentes, ni grupos de más de 2 personas"), más lo que
la v1.0 dejó pendiente. **Casi todo esto hoy figura como "fuera de alcance" en
CLAUDE.md**: al empezar cada uno, actualizar esa sección. Los **presupuestos
siguen descartados** (ingresos irregulares: ver la entrevista).

Cada ítem trae las preguntas que hay que contestar **antes** de programarlo.

### 2.1 Sumarse a un grupo (códigos por grupo) — la base de todo lo demás

Hoy cada cuenta nueva queda sola en su grupo, y **no hay forma de sumar a otra
persona**: la sección de pareja solo existe para Viole y Franco porque se
registraron antes de la v1.0. Sin esto, nadie más puede usar lo compartido.

- Un código por grupo, que genera quien ya está adentro (Ajustes → "Invitar").
  ¿Vence? ¿Se usa una sola vez? ¿Se puede regenerar?
- ¿Qué pasa con los gastos PERSONAL de quien se suma y abandona su grupo de uno?
  (Se mudan con la persona: siguen siendo suyos y privados.)
- Vuelve el tope de integrantes (hoy `MAXIMO_INTEGRANTES` no existe; se sacó con
  el registro abierto). Ver 2.2.
- `tienePareja` en el resumen ya existe: la app muestra la sección de pareja sola
  en cuanto el grupo tenga dos.
- Salir de un grupo: ¿se puede? Hoy la única salida es borrar la cuenta.

### 2.2 Grupos de más de 2 personas — el cambio de modelo más grande

El modelo asume dos en tres lugares, y los tres se rompen:

- **El reparto**: `Gasto.montoPagador` solo dice cuánto le toca a quien pagó; con
  tres, no alcanza. El camino ya está escrito en CLAUDE.md: una lista
  `participaciones: [{usuarioId, monto}]` embebida en el gasto (en Mongo, no una
  tabla). Con **migración de los gastos existentes** (escritura en producción,
  con permiso).
- **El saldo**: hoy es "quién le debe a quién" entre dos. Con N es un grafo de
  deudas; hay que simplificarlo (minimizar transferencias).
- **La UI**: "Pagué yo / Pagó Viole" pasa a ser un selector de persona, y el
  reparto 50/50 pasa a "partes iguales" o montos por persona. Cuidado con el
  requisito duro de velocidad de carga.
- La vaquita ya soporta N aportantes (`porPersona`).
- **Pregunta de producto**: ¿para qué lo quieren? ¿Un viaje con amigos? Si es eso,
  quizás alcanza con que la **vaquita** admita más personas que el grupo.

### 2.3 Balance / saldar deudas

Hoy el saldo es del mes y no hay forma de registrar "ya te pagué".

- La entidad está pensada en CLAUDE.md: `Liquidacion(grupo, de, para, monto,
  fecha)` y `saldo = deudas - pagos`, histórico y no mensual.
- ¿Se muestra el histórico acumulado, o por mes con arrastre?
- Un aporte a la vaquita ya es una liquidación anticipada: reusar el concepto.

### 2.4 Dólares (multimoneda)

- ¿Moneda por gasto (pesos o dólares) y totales separados, o todo convertido a
  pesos? Convertir exige una cotización: ¿oficial, blue, tarjeta? ¿De qué fuente,
  y congelada en el gasto al cargarlo (como el reparto) o recalculada?
- **El total hormiga y el ánimo de la nutria** no pueden mezclar monedas sin
  convertir. Recomendación de arranque: moneda por gasto, cotización congelada al
  cargar, y los agregados en pesos.
- Un campo más en el alta choca con la velocidad: moneda por defecto, y cambiarla
  con un toque (quizás "moneda del viaje" en la vaquita).
- **¿El viaje es afuera?** Si es así, esto es lo primero a tener antes del viaje.

### 2.5 Gráficos

- ¿Qué gráfico contesta qué pregunta? Candidatos: gasto hormiga por mes (la
  tendencia que ya calcula la nutria), total por categoría.
- Respetar la regla del ánimo: medidas relativas, nunca un tope fijo.
- El ámbar sigue siendo solo del gasto hormiga, también en los gráficos.
- Librería: medir el bundle antes y después (ver "Metro no hace tree-shaking" en
  CLAUDE.md). Alternativa liviana: dibujarlo con `react-native-svg`, que ya está.

### 2.6 Notificaciones push

- ¿Para qué? Candidatos: la otra persona cargó un compartido, recordatorio de
  cargar, la vaquita se está quedando sin plata. Preguntarle a Viole: una app de
  finanzas que molesta se desinstala.
- Técnico: `expo-notifications` + clave APNs (EAS la gestiona), guardar el push
  token por usuario, y mandar desde el backend por la API HTTPS de Expo (Render
  no la bloquea).
- Actualizar la **privacidad** en App Store Connect (el push token es un
  identificador) y la política de Notion.

### 2.7 Gastos recurrentes

- Alquiler, suscripciones, servicios. ¿Se cargan solos, o la app sugiere "¿ya
  pagaste X este mes?" con un toque?
- Render gratis duerme: un job programado no es confiable. Generarlos al leer el
  mes (el primer GET del mes los materializa, con idempotencia por
  `clienteId`) evita el scheduler.
- ¿Cuentan como hormiga? Por definición, casi nunca.

---

## 3. Deuda técnica y riesgos conocidos

- **El registro revela qué emails tienen cuenta** ("Ese email ya está
  registrado"). Ahora que hay mails (API de Gmail), se puede cerrar: verificar el
  email con un código al registrarse, igual que el reseteo.
- **`version` obligatorio en el PUT** de gastos (pendiente de CLAUDE.md): la app
  ya lo manda siempre.
- **Ícono de Android** (`adaptiveIcon`): quedó con los archivos de ejemplo de
  Expo. Hace falta la nutria sola sobre fondo transparente, regenerada desde la
  herramienta original. Solo si algún día hay build de Android.
- **El token de Gmail** se revoca si cambia la contraseña de la cuenta de Google,
  o tras 6 meses sin usarse. Si los mails de reseteo dejan de llegar, ver
  `docs/mails.md`.
- **El limitador de intentos vive en memoria**: se reinicia con cada deploy y no
  sirve con más de una instancia. Alcanza mientras haya una sola.
- **La web** (`/web`, React + Vite) sigue pendiente, y `SecureStore` no existe en
  web: necesita otra capa de sesión. Tiene que usar la misma tabla de
  `nombreDeCategoria`.

---

## 4. Orden sugerido, pensando en el viaje

1. Sección 1 (cerrar la v1.0), sobre todo **la cuenta demo el 1/10**.
2. **Dólares (2.4)**, si el viaje es afuera: es lo único de la lista que cambia
   cómo se usa la app en el viaje.
3. **Sumarse a un grupo (2.1)**: destraba lo compartido para cualquiera, y es
   requisito de 2.2.
4. **Balance / saldar (2.3)**: cierra las cuentas de la vuelta del viaje.
5. Gráficos, recurrentes y push, según lo que pida Viole.
6. Más de 2 personas (2.2) al final: es el cambio de modelo más caro, y quizás
   alcanza con una vaquita de más personas.

**Cada feature**: backend con tests → smoke test (en la PC) → app → `tsc` →
push → CI en verde → build de EAS (`--auto-submit` ya sube solo) → TestFlight
con Viole. **El backend se deploya antes que la app** siempre que cambie un
contrato. La versión de la app pasa a `1.1.0` en `mobile/app.json`.
