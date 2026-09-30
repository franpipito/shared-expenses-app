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

## 0. Lo que se hizo en la sesión de la nube (2.1 + 2.3): probarlo primero

Antes de leer nada más, esto es lo que hay que verificar apenas te sientes
con la PC. Todo el código ya está commiteado localmente; falta pushearlo
(quedaron los bloques de `git add`/`git commit` listos en la conversación de
esa sesión) y probarlo de verdad, que es algo que la nube no puede hacer.

**Lo que se construyó, completo (backend + mobile) en las dos features:**

- **2.1 — Sumarse a un grupo.** `POST /grupo/invitar`, `/sumarse`, `/salir`.
  En mobile: `app/grupo.tsx` (invitar/sumarse en una pantalla) y
  `app/salir-del-grupo.tsx`, con Ajustes decidiendo cuál mostrar.
- **2.3 — Saldar cuentas.** `GET /saldo/total` y `POST`/`GET` sobre
  `/saldo/liquidaciones`. En mobile: `app/liquidaciones.tsx`, linkeado desde
  `saldo.tsx` como "Saldar cuentas (toda la historia)".
- De paso: `npm audit fix` en mobile (lockfile al día), y se confirmó que el
  dependency-check de OWASP para el backend no se puede correr desde la nube
  (bloqueado por la política de red del sandbox) — hace falta tu PC o
  sumarlo al CI, ver sección 3.

Detalle completo de cada decisión de diseño en `CLAUDE.md`, buscando
"Sumarse a un grupo" y "Saldar deudas".

### Paso a paso para probarlo

**1. Pushear.** `git push` con lo que ya está commiteado. Esperá a que el CI
   dé verde (`contextLoads` corre ahí, con Mongo de verdad — nunca corrió en
   la nube) y a que Render termine de deployar (mirá el dashboard; el
   backend viejo no tiene estos endpoints todavía).

**2. Correr el smoke test completo, con Docker:**
   ```powershell
   docker compose up -d
   cd backend; $env:REGISTRO_MAX_POR_IP = "100"; .\mvnw.cmd spring-boot:run
   # en otra terminal:
   .\scripts\smoke-test.ps1
   ```
   Prestale atención a las secciones **14** (sumarse/invitar/salir) y **15**
   (liquidaciones) — son nuevas y nunca corrieron contra Mongo real. Si algo
   falla ahí, es la primera vez que se prueba de verdad el índice único
   parcial de `invitacion_codigo` y que `findAndModify` sea atómico.

**3. `cd backend; .\mvnw.cmd test`** completo, para ver `contextLoads` en
   verde por tu cuenta (el CI ya lo confirmó, pero vale la corrida local).

**4. Probar 2.1 en el teléfono** (Expo Go o tu dev client alcanza — no se
   agregó ninguna dependencia nativa nueva, así que no hace falta un build
   de EAS nuevo para esto):
   - Con una cuenta sola, Ajustes → "Sumarse a un grupo" → pestaña
     "Invitar" → "Generar código" → "Compartir".
   - Con otra cuenta (o la de Viole), pegar el código en "Ya tengo un
     código" → "Sumarme".
   - Confirmar que las dos ven a la otra persona en Ajustes.
   - Cargar un gasto COMPARTIDO desde una y verlo aparecer para la otra.
   - Probar "Salir del grupo": confirmar con la contraseña, y verificar que
     el compartido sigue en el historial de quien se queda, **con el nombre
     real** (no "Cuenta eliminada": la cuenta no se borró).

**5. Probar 2.3 en el teléfono**, con las dos cuentas ya juntas:
   - Cargar un COMPARTIDO con reparto (para tener algo que saldar).
   - Saldo → "Saldar cuentas (toda la historia)". El número tiene que
     coincidir con el saldo del mes si no hay más historia previa.
   - "Marcar como pagado" con un monto MENOR al total (pago parcial):
     confirmar que el número baja y aparece en el historial.
   - Desde la OTRA cuenta (la que cobra), marcar el resto como pagado: esto
     ejercita `meLoPagaron`, que nunca se probó en un teléfono. Confirmar
     que también funciona y que el historial muestra la dirección correcta
     de los dos pagos.
   - Confirmar que terminan en "Están a mano".

Si algo de esto no anda, es información real que la nube no podía darte —
avisá en la próxima sesión y se corrige antes de seguir con lo de Viole.

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

**Backend hecho** (sesión en la nube, sin PC): `POST /grupo/invitar`,
`POST /grupo/sumarse`, `POST /grupo/salir`. Diseño completo y el porqué de
cada decisión en `CLAUDE.md` → "Sumarse a un grupo: códigos de invitación".
Resumen rápido de lo que se resolvió:

- **El código vence a los 7 días y NO se guarda hasheado** (a diferencia del
  de reseteo): tiene mucha más entropía y hace falta poder buscarlo directo
  por su valor, porque quien se suma no sabe de qué grupo es.
- **El tope sigue en dos.** Subirlo es 2.2, no esto.
- **Los PERSONAL de quien se suma se mudan con la persona** (`GastoConsultas.
  moverPersonalesA`), como ya se había anticipado acá.
- **Salir es simétrica: cualquiera puede salir cuando quiere, sin una acción
  separada para "expulsar" a la otra persona.** Surgió de preguntar "¿y si
  se pelean?": con el tope en dos, "me voy yo" y "te saco a vos" llegan al
  mismo estado final, así que alcanza con una sola acción — y conviene que
  sea la única, porque dejar que alguien saque a otro de un grupo compartido
  en medio de una pelea es abrirle la puerta a usarlo como control.

**Mobile también hecho**, en la misma sesión: `app/grupo.tsx` (invitar y
sumarse, un solo lugar) y `app/salir-del-grupo.tsx` (mismo molde que
borrar-cuenta). Ajustes ya decide cuál mostrar según `GET /grupo`.
Verificado con `tsc --noEmit` y `expo export` — no en un teléfono.

**Falta, y necesita la PC**: correr `scripts/smoke-test.ps1` (ya tiene los
chequeos de la sección 14, sin correr — es lo único que prueba el índice
único parcial de `invitacion_codigo` y que `findAndModify` es atómico
contra Mongo real) y probar las pantallas nuevas en un teléfono de verdad.
Los 9 tests nuevos de `GrupoServicioTest` (mocks) pasan junto con los 144
que ya había; `contextLoads` no se pudo correr en esta sesión (sin Mongo).

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

### 2.3 Saldar deudas entre la pareja

**Backend hecho** (misma sesión en la nube que 2.1): `GET /saldo/total`,
`POST /saldo/liquidaciones`, `GET /saldo/liquidaciones`, en
`LiquidacionServicio`/`LiquidacionControlador`. `GET /saldo` (del mes) no
cambió. Diseño completo en `CLAUDE.md` → "Saldar deudas: `Liquidacion`, sin
acotar a un viaje". Se resolvió la pregunta pendiente: **un número solo**
("te deben $X"), no un extracto mes a mes con arrastre — misma forma que ya
usa la vaquita.

De paso se subió `otroIntegranteDe` a `UsuarioRepositorio` (lo necesitaban
`ResumenServicio` y `LiquidacionServicio` por igual) y el refactor rompió
dos tests que ya estaban en verde — corregido, y vale como anécdota de
"Los tests: dos capas" en CLAUDE.md.

**Falta**: la pantalla en mobile (el número en saldo, "Marcar como pagado",
historial) y los chequeos en `scripts/smoke-test.ps1`. 151 tests en verde
(144 + 7 nuevos de `LiquidacionServicioTest`), salvo `contextLoads`.

### 2.3b Balance personal de Viole (distinto de lo anterior)

No confundir con 2.3: esto es la plata de Viole sola, no lo que se deben entre
los dos. Franco le preguntó si prefiere que la app solo sume sus gastos, o que
tenga un "balance" que se va descontando con cada gasto -- **la respuesta
nunca quedó registrada en `docs/entrevista-usuaria.md`**. Confirmarla antes de
diseñar nada de esto.

Si la respuesta es que sí quiere el balance:
- **Riesgo de fondo**: el número solo va a ser tan cierto como lo que ella
  cargue. Un movimiento que no pase por la app (efectivo, una transferencia)
  lo desalinea de su plata real -- puede ser peor que no tener el número.
  Preguntarle si lo entiende como "lo que cargué en la app" y no como su saldo
  bancario real.
- **Nombre**: no usar "Saldo", ya significa "quién le debe a quién" entre la
  pareja. Un nombre distinto (por ejemplo "Mi Plata") para no confundirlos.

### 2.4 Ahorro en dólares (no es multimoneda de gastos)

Ojo con esto: ya se le preguntó puntualmente a Franco si era "a veces paga un
gasto en dólares" o "tiene un ahorro aparte", y contestó **lo segundo**. No es
un campo de moneda en `Gasto` ni conversión de agregados -- es un concepto
nuevo y separado, más parecido a una caja de ahorro que a un gasto. No toca
`montoPagador` ni el ánimo de la nutria.

Sigue pendiente de Viole (tampoco quedó registrado en
`docs/entrevista-usuaria.md`): dónde tiene ese ahorro (efectivo, cuenta,
broker), si lo carga a mano cada vez o espera algo automático, y si lo quiere
ver convertido a pesos o solo en dólares.

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
