# Mails: el código de "olvidé mi contraseña"

El backend manda el código por la **API de Gmail**, desde tu cuenta de Gmail,
con el nombre "MiNutria". Gratis, y con un tope de unos 500 mails por día.

**Por qué así y no de otra forma**, en corto:

- **SMTP no**: Render gratis bloquea la salida a los puertos SMTP (25, 465 y 587)
  desde septiembre de 2025. La API de Gmail va por HTTPS, que sí sale.
- **Resend, Brevo, etc. no**: sin dominio propio no pueden autenticar un
  remitente `@gmail.com`, y esos mails terminan en spam o rechazados. Por la API
  de Gmail el mail sale de los servidores de Google, firmado como gmail.com.
- **Firebase Auth no**: resetea contraseñas de usuarios de Firebase, y los de
  MiNutria viven en Mongo con BCrypt. Habría que mudar todo el login.

**Mientras no configures esto, la app funciona igual**: el código se escribe en
el log del servidor en vez de mandarse (`EnviadorPorLog`). En Render lo ves en
Logs, buscando `MAIL SIN ENVIAR`. Sirve para ayudar a mano, pero **configuralo
antes de mandar a revisión**: el revisor puede probar el botón.

---

## Configuración (una sola vez, unos 15 minutos)

### 1. El proyecto en Google Cloud

1. Entrá a https://console.cloud.google.com con tu cuenta de Gmail y creá un
   proyecto nuevo, por ejemplo `MiNutria`.
2. **APIs y servicios → Biblioteca**: buscá **Gmail API** y tocá **Habilitar**.

### 2. La pantalla de consentimiento

En **Google Auth Platform** (antes "Pantalla de consentimiento de OAuth"):

1. **Branding**: nombre `MiNutria`, tu email de soporte.
2. **Público**: tipo **Externo**.
3. **Publicar la app** → queda **En producción**.

> **Tiene que quedar en producción, no en "Prueba".** En prueba, el refresh token
> vence a los 7 días y los mails dejan de salir solos una semana después. En
> producción sin verificar, Google muestra un aviso de "app no verificada" al
> autorizar, pero es solo para vos y una sola vez (el tope es de 100 usuarios,
> y el único que autoriza sos vos).

### 3. Las credenciales

**Clientes → Crear cliente**:

- Tipo: **Aplicación web**.
- URI de redireccionamiento autorizado: `https://developers.google.com/oauthplayground`
- Guardá el **ID de cliente** y el **secreto del cliente**.

### 4. El refresh token

1. Entrá a https://developers.google.com/oauthplayground
2. Engranaje de arriba a la derecha → **Use your own OAuth credentials** → pegá el
   ID y el secreto del paso 3.
3. En **Step 1**, escribí este scope en el campo de abajo y tocá **Authorize APIs**:

   ```
   https://www.googleapis.com/auth/gmail.send
   ```

   Es el único permiso que se pide: **mandar** mails. No puede leer tu casilla.
4. Entrá con tu cuenta. Cuando diga que la app no está verificada: **Configuración
   avanzada → Ir a MiNutria**, y permití.
5. En **Step 2**, tocá **Exchange authorization code for tokens** y copiá el
   **Refresh token**.

### 5. Las variables en Render

En el servicio `minutria-api` → **Environment**, agregá:

| Variable | Valor |
|---|---|
| `GMAIL_CLIENT_ID` | el ID de cliente |
| `GMAIL_CLIENT_SECRET` | el secreto del cliente |
| `GMAIL_REFRESH_TOKEN` | el refresh token |
| `MAIL_REMITENTE` | tu dirección de Gmail |

Al guardar, Render redeploya. En los logs del arranque tiene que aparecer
`Mails: por la API de Gmail, desde ...`. Si dice `no hay credenciales`, falta
alguna de las cuatro.

### 6. Probarlo

En la app: **¿Olvidaste tu contraseña?** con tu propio email. El mail llega de
"MiNutria" con el código. Si no llega, fijate en spam, y en los logs de Render
buscá `No salio el mail de reseteo`, que trae el motivo que dio Google.

---

## Cosas que conviene saber

- **Si cambiás la contraseña de tu cuenta de Google, el refresh token se revoca**
  (Google lo hace con los permisos de Gmail). Los mails dejan de salir y hay que
  repetir el paso 4 y actualizar `GMAIL_REFRESH_TOKEN`.
- **Un refresh token que no se usa en 6 meses también vence.** Con una app en uso
  no debería pasar, pero si nadie se olvida la contraseña en medio año, puede.
- **El mail sale de tu Gmail**: quien responda al mail te escribe a vos. Es el
  mismo contacto que ya figura en la página de soporte.
