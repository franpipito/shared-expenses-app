# Smoke test de la API de gastos.
#
# Requiere:
#   - Mongo local en Docker (docker compose up -d): el script lo usa directo para
#     juntar a Franco y Ella en un grupo, ver "0. Autenticacion"
#   - la app corriendo en localhost:8080 CON EL TOPE DE REGISTROS SUBIDO:
#       $env:REGISTRO_MAX_POR_IP = "100"; .\mvnw.cmd spring-boot:run
#     El default es 5 registros por IP cada 15 minutos (el valor de produccion),
#     y este script registra varios usuarios seguidos desde 127.0.0.1.
#   - las categorias sembradas (las siembra la app sola al arrancar)
#
# Correr desde la raiz del repo:
#   .\scripts\smoke-test.ps1
#
# Los usuarios los crea el propio script contra /auth/registro la primera vez, y
# despues entra por /auth/login. Los gastos de prueba los BORRA al final; los
# usuarios quedan, que es lo que permite volver a correrlo sin limpiar nada.

$ErrorActionPreference = "Stop"

$base     = "http://localhost:8080"
$PASSWORD = "gastos-dev-2026"

# Los ids de categoria se resuelven POR NOMBRE, mas abajo, una vez que hay token.
#
# Antes estaban hardcodeados ($CAFE = 1) y funcionaba porque la migracion V2 de
# Flyway las insertaba siempre en el mismo orden, asi que los ids eran estables
# entre bases recreadas. Con Mongo el _id es un ObjectId distinto en cada
# siembra, asi que hay que preguntarle a la API cual es cual.
$CAFE = $null; $UBER = $null; $COMIDA = $null

$fallos = 0

function Titulo($t) { Write-Host "`n=== $t ===" -ForegroundColor Cyan }
function Chequear($condicion, $texto) {
    if ($condicion) {
        Write-Host "  OK   $texto" -ForegroundColor Green
    } else {
        Write-Host "  MAL  $texto" -ForegroundColor Red
        $script:fallos++
    }
}

function Crear($headers, $hash) {
    $json = $hash | ConvertTo-Json
    return Invoke-RestMethod -Uri "$base/gastos" -Method Post -Headers $headers `
        -ContentType "application/json" -Body $json
}

function EsperarCodigo($bloque, $esperado, $texto) {
    try {
        & $bloque | Out-Null
        Chequear $false "$texto (no dio error)"
    } catch {
        $codigo = $_.Exception.Response.StatusCode.value__
        Chequear ($codigo -eq $esperado) "$texto (dio $codigo, esperaba $esperado)"
    }
}

function CuerpoDelError($err) {
    try {
        $stream = $err.Exception.Response.GetResponseStream()
        $stream.Position = 0
        return (New-Object System.IO.StreamReader($stream)).ReadToEnd()
    } catch {
        return ""
    }
}

# Un 400 no alcanza: hay que verificar POR QUE dio 400.
# Un JSON mal formado tambien devuelve 400, y entonces el chequeo pasaria en
# verde sin que la validacion que decimos probar se haya ejecutado nunca.
# Bean Validation es la unica que produce un cuerpo con "errores".
function EsperarValidacion($bloque, $campo, $texto) {
    try {
        & $bloque | Out-Null
        Chequear $false "$texto (no dio error)"
    } catch {
        $codigo = $_.Exception.Response.StatusCode.value__
        $cuerpo = CuerpoDelError $_
        $ok = ($codigo -eq 400) -and ($cuerpo -match '"errores"') -and ($cuerpo -match $campo)
        Chequear $ok "$texto -> $cuerpo"
    }
}

# Sin contenido: 204 con el cuerpo vacio.
#
# Chequea el CODIGO y no el cuerpo, y la diferencia no es cosmetica: ante un 204
# Invoke-RestMethod de PowerShell 5.1 devuelve un string vacio, no $null, asi que
# un `$null -eq $respuesta` da falso y el chequeo falla aunque el backend haya
# hecho exactamente lo que debia. Ademas el contrato que queremos probar es
# justamente ese: **204 y no 404**, porque no tener vaquita es un estado normal
# de la app y no un error.
#
# Hace falta Invoke-WebRequest y no Invoke-RestMethod porque el segundo devuelve
# el cuerpo ya deserializado y se come el codigo de estado.
function EsperarSinContenido($uri, $headers, $texto) {
    $r = Invoke-WebRequest -Uri $uri -Headers $headers -UseBasicParsing
    Chequear ($r.StatusCode -eq 204) "$texto (dio $($r.StatusCode), esperaba 204)"
}

# Reglas de negocio: 400 con un `mensaje`, no con `errores`.
function EsperarRegla($bloque, $textoEsperado, $texto) {
    try {
        & $bloque | Out-Null
        Chequear $false "$texto (no dio error)"
    } catch {
        $codigo = $_.Exception.Response.StatusCode.value__
        $cuerpo = CuerpoDelError $_
        $ok = ($codigo -eq 400) -and ($cuerpo -match $textoEsperado)
        Chequear $ok "$texto -> $cuerpo"
    }
}

function Registrar($nombre, $email, $password) {
    $body = @{ nombre = $nombre; email = $email; password = $password } | ConvertTo-Json
    return Invoke-RestMethod -Uri "$base/auth/registro" -Method Post `
        -ContentType "application/json" -Body $body
}

function Entrar($email, $password) {
    $body = @{ email = $email; password = $password } | ConvertTo-Json
    return Invoke-RestMethod -Uri "$base/auth/login" -Method Post `
        -ContentType "application/json" -Body $body
}

# Corre JS contra la Mongo local y devuelve lo que imprime. Solo para lo que la
# API no sabe hacer (juntar dos cuentas en un grupo) o no deja ver (lo que queda
# en la base despues de un borrado).
#
# Comillas simples adentro del JS, a proposito: PowerShell 5.1 le pasa mal las
# comillas dobles a un ejecutable nativo.
function Mongo($js) {
    $salida = docker exec gastos-mongo mongosh -u gastos -p gastos_local --authenticationDatabase admin gastos --quiet --eval $js
    return "$salida".Trim()
}

# Mueve la cuenta de $emailQueSeSuma al grupo de $emailDelGrupo. Ver "0.
# Autenticacion" para el por que.
function JuntarEnGrupo($emailQueSeSuma, $emailDelGrupo) {
    $unidos = Mongo ("const f = db.usuario.findOne({ email: '$emailDelGrupo' });" +
        "const r = db.usuario.updateOne({ email: '$emailQueSeSuma' }, { `$set: { grupo_id: f.grupo_id } });" +
        "print(r.matchedCount);")
    if ($unidos -ne "1") {
        Write-Host ""
        Write-Host "  No se pudo juntar $emailQueSeSuma con $emailDelGrupo via mongosh (dijo: $unidos)." -ForegroundColor Red
        Write-Host "  Este script necesita el contenedor gastos-mongo corriendo: docker compose up -d" -ForegroundColor Yellow
        Write-Host ""
        exit 1
    }
}

# Registra si no existe, y si ya existe entra. Asi el script se puede correr
# muchas veces seguidas sin tener que limpiar usuarios entre corridas.
#
# El catch mira POR QUE fallo el registro. Un catch a secas que cayera siempre al
# login esconde el error real: si el registro falla por otra cosa, lo que ves es
# un "email o contrasena incorrectos" que no tiene nada que ver con la causa.
function RegistrarOEntrar($nombre, $email, $password) {
    try {
        return Registrar $nombre $email $password
    } catch {
        $cuerpo = CuerpoDelError $_
        if ($cuerpo -match "registrado") {
            return Entrar $email $password
        }
        if ($_.Exception.Response.StatusCode.value__ -eq 429) {
            Write-Host ""
            Write-Host "  El backend corto los registros por IP (429)." -ForegroundColor Red
            Write-Host "  El tope default es 5 cada 15 minutos. Reinicia la API con:" -ForegroundColor Yellow
            Write-Host ""
            Write-Host '      $env:REGISTRO_MAX_POR_IP = "100"; .\mvnw.cmd spring-boot:run'
            Write-Host ""
            exit 1
        }
        throw "No se pudo registrar a $nombre, y NO es porque ya exista. El backend dijo: $cuerpo"
    }
}

# ---------------------------------------------------------------------------
# ANTES DE NADA: que el backend conteste.
#
# POR QUE EXISTE ESTE CHEQUEO. Sin el, un backend apagado se veia asi:
#
#   No se pudo registrar a Franco, y NO es porque ya exista. El backend dijo:
#
# ...y nada atras. El mensaje era tecnicamente correcto -- el backend no dijo
# nada, porque no habia backend -- pero manda a buscar el problema al lugar
# equivocado: uno se pone a mirar el registro, el codigo de invitacion o la
# base, cuando lo unico que pasaba es que faltaba levantar la API.
#
# Un chequeo que falla tiene que decir QUE hay que hacer. Este corta en la
# primera linea, con el comando adentro del mensaje.
try {
    $salud = Invoke-RestMethod -Uri "$base/actuator/health" -TimeoutSec 10
    if ($salud.status -ne "UP") {
        Write-Host "El backend contesta pero no esta sano: status = $($salud.status)" -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host ""
    Write-Host "  No hay backend escuchando en $base" -ForegroundColor Red
    Write-Host ""
    Write-Host "  Levantalo en otra terminal, en este orden:" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "      docker compose up -d                    # desde la RAIZ del repo"
    Write-Host '      cd backend; $env:REGISTRO_MAX_POR_IP = "100"; .\mvnw.cmd spring-boot:run'
    Write-Host ""
    Write-Host "  El orden importa: sin Mongo, la API no arranca." -ForegroundColor Yellow
    Write-Host ""
    exit 1
}

# ---------------------------------------------------------------------------
Titulo "0. Autenticacion"

$sesionFranco = RegistrarOEntrar "Franco" "franco@local" $PASSWORD
$sesionElla   = RegistrarOEntrar "Ella"   "ella@local"   $PASSWORD

# A partir de aca, en vez del header X-Usuario-Id de la sesion 2 va el token.
$franco = @{ Authorization = "Bearer $($sesionFranco.token)" }
$ella   = @{ Authorization = "Bearer $($sesionElla.token)" }
$FRANCO_ID = $sesionFranco.usuario.id
$ELLA_ID   = $sesionElla.usuario.id

Chequear ($sesionFranco.token.Length -gt 50)      "el registro/login devuelve un token"
Chequear ($sesionFranco.usuario.nombre -eq "Franco") "y devuelve el usuario, sin una segunda llamada"
Chequear ($null -eq $sesionFranco.usuario.email)  "el usuario de la respuesta no expone el email"
Chequear ($sesionFranco.token.Split('.').Count -eq 3) "el token tiene las tres partes de un JWT"
Chequear ($FRANCO_ID -ne $ELLA_ID)                "son dos usuarios distintos"

# --- juntarlos en un grupo, por la base ---
# Desde la v1.0 el registro es abierto y CADA cuenta nueva crea su propio grupo,
# asi que Ella acaba de caer en uno distinto al de Franco. La API todavia no
# sabe sumar a alguien a un grupo ajeno (eso es la v1.1, con codigos por grupo).
#
# Este updateOne deja a los dos en el mismo grupo, que es exactamente el estado
# de Viole y Franco en produccion: se registraron antes de la v1.0, cuando el
# segundo se sumaba solo. Es idempotente: en las corridas siguientes ya estan
# juntos y no cambia nada. El grupo que Ella tenia queda vacio; nadie lo ve.
JuntarEnGrupo "ella@local" "franco@local"

EsperarCodigo { Entrar "franco@local" "contrasena-incorrecta" } `
    401 "contrasena incorrecta da 401"

# El MISMO mensaje que el anterior: si dijera "ese email no existe", el login
# seria un verificador de que cuentas estan registradas.
$errorEmailInexistente = $null
try { Entrar "nadie@local" $PASSWORD } catch { $errorEmailInexistente = CuerpoDelError $_ }
$errorPasswordMala = $null
try { Entrar "franco@local" "otra-cosa" } catch { $errorPasswordMala = CuerpoDelError $_ }
Chequear ($errorEmailInexistente -eq $errorPasswordMala) `
    "email inexistente y contrasena mala dan el mismo error, sin filtrar cuales existen"

EsperarRegla { Registrar "Franco" "franco@local" $PASSWORD } `
    "registrado" "no se puede registrar dos veces el mismo email"

EsperarCodigo { Invoke-RestMethod -Uri "$base/gastos?mes=2026-09" `
    -Headers @{ Authorization = "Bearer esto.no.es-un-token" } } `
    401 "un token invalido da 401"

# El header viejo de la sesion 2 ya no autentica nada.
EsperarCodigo { Invoke-RestMethod -Uri "$base/gastos?mes=2026-09" `
    -Headers @{ "X-Usuario-Id" = "1" } } `
    401 "el header X-Usuario-Id ya no sirve"

# --- politica de contrasenas ---
EsperarRegla { Registrar "Corta" "corta@local" "Abc123!x" } `
    "al menos 12" "una contrasena de menos de 12 caracteres se rechaza"

EsperarRegla { Registrar "Comun" "comun@local" "123456789012" } `
    "demasiado com" "una contrasena comun se rechaza aunque sea larga"

EsperarRegla { Registrar "Homonimo" "homonimo@local" "homonimo-del-sur" } `
    "no puede contener tu email" "no se puede usar el propio email como contrasena"

# --- limite de intentos ---
# Contra un email inexistente A PROPOSITO: si machacaramos franco@local,
# quedaria bloqueado 15 minutos y las corridas siguientes del script fallarian.
# El limite por IP es mucho mas alto (20), asi que estos 6 fallos no dejan
# afuera al resto de los chequeos.
1..5 | ForEach-Object {
    try { Entrar "fuerzabruta@local" "intento-numero-$_" } catch { }
}
EsperarCodigo { Entrar "fuerzabruta@local" "otro-intento-mas" } `
    429 "al sexto intento fallido contra la misma cuenta responde 429"

# --- revocacion de tokens ---
# El caso "perdi el celular": el token viejo tiene firma valida y no expiro,
# pero igual queda afuera porque su generacion quedo vieja.
$tokenViejoDeFranco = $sesionFranco.token
$sesionNueva = Invoke-RestMethod -Uri "$base/auth/cerrar-sesiones" -Method Post -Headers $franco
Chequear ($sesionNueva.token -ne $tokenViejoDeFranco) "cerrar-sesiones devuelve un token nuevo"

EsperarCodigo { Invoke-RestMethod -Uri "$base/gastos?mes=2026-09" `
    -Headers @{ Authorization = "Bearer $tokenViejoDeFranco" } } `
    401 "el token anterior queda invalidado aunque no haya expirado"

# El resto del script sigue con el token nuevo.
$franco = @{ Authorization = "Bearer $($sesionNueva.token)" }
$categorias = Invoke-RestMethod -Uri "$base/categorias" -Headers $franco
Chequear (($categorias | Measure-Object).Count -eq 6) "el token nuevo funciona"

# Resolver los ids por nombre, ahora que son ObjectId y no numeros.
function IdDeCategoria($nombre) {
    $c = $categorias | Where-Object { $_.nombre -eq $nombre }
    if (-not $c) { throw "No aparecio la categoria '$nombre' en GET /categorias" }
    return $c.id
}
$CAFE   = IdDeCategoria "cafe"
$UBER   = IdDeCategoria "uber"
$COMIDA = IdDeCategoria "comida"
Chequear ($CAFE -is [string] -and $CAFE.Length -eq 24) "los ids de categoria son ObjectId de 24 caracteres"

# Y el de Ella no se toca: cerrar sesiones es por usuario, no global.
$catsElla = Invoke-RestMethod -Uri "$base/categorias" -Headers $ella
Chequear (($catsElla | Measure-Object).Count -eq 6) "la sesion de Ella no se vio afectada"

# ---------------------------------------------------------------------------
Titulo "0.1 El grupo y sus integrantes"

# GET /grupo existe para que la app pueda ofrecer "lo pago la otra persona" al
# cargar un COMPARTIDO: sin esto el cliente no tiene forma de saber el id del
# otro integrante.
$grupoFranco = Invoke-RestMethod -Uri "$base/grupo" -Headers $franco
$grupoElla   = Invoke-RestMethod -Uri "$base/grupo" -Headers $ella

Chequear ($grupoFranco.id -is [string] -and $grupoFranco.id.Length -eq 24) `
    "el grupo tiene un id de ObjectId"
Chequear (($grupoFranco.integrantes | Measure-Object).Count -eq 2) `
    "el grupo tiene dos integrantes"
Chequear ($grupoFranco.id -eq $grupoElla.id) `
    "los dos ven el mismo grupo"

# El endpoint NO acepta un id por parametro justamente para que no se pueda
# pedir el grupo de otro. Lo que se devuelve sale del token.
$nombres = ($grupoFranco.integrantes | ForEach-Object { $_.nombre }) | Sort-Object
Chequear (($nombres -join ",") -eq "Ella,Franco") `
    "estan los dos por nombre"

# Un DTO lleva lo que hace falta y ni un campo mas. El email es dato de contacto
# que la app no necesita, y este chequeo es lo que evita que alguien lo agregue
# sin darse cuenta mas adelante.
$tieneEmail = $grupoFranco.integrantes | Where-Object { $null -ne $_.email }
Chequear ($null -eq $tieneEmail) "los integrantes NO exponen el email"

$idDeElla = ($grupoFranco.integrantes | Where-Object { $_.nombre -eq "Ella" }).id
Chequear ($idDeElla -is [string] -and $idDeElla.Length -eq 24) `
    "de aca sale el id que necesita el alta para 'lo pago el otro'"

EsperarCodigo { Invoke-RestMethod -Uri "$base/grupo" } 401 `
    "sin token, /grupo da 401"

# cerrar-sesiones y borrar-cuenta viven bajo /auth/, pero a diferencia de
# registro/login/olvide-contrasena/restablecer-contrasena SI exigen token.
# Hasta la auditoria de seguridad de la v1.0, un unico
# .requestMatchers("/auth/**").permitAll() dejaba pasar los dos en esta capa,
# y quedaban protegidos solo porque el servicio llama a
# UsuarioActual.requerido(). Esto prueba la cadena de filtros de verdad, que
# ningun mock de servicio puede ejercitar.
EsperarCodigo { Invoke-RestMethod -Uri "$base/auth/cerrar-sesiones" -Method Post } 401 `
    "sin token, /auth/cerrar-sesiones da 401"
EsperarCodigo { Invoke-RestMethod -Uri "$base/auth/borrar-cuenta" -Method Post `
    -ContentType "application/json" -Body (@{ password = "cualquiera12345" } | ConvertTo-Json) } 401 `
    "sin token, /auth/borrar-cuenta da 401"

# Una ruta que no existe tiene que dar 404, no 401.
#
# Parece un detalle y no lo es: Boot reenvia el 404 a /error, ese reenvio vuelve
# a pasar por la cadena de filtros, y FiltroJwt NO corre la segunda vez
# (OncePerRequestFilter.shouldNotFilterErrorDispatch viene en true). Sin
# permitAll sobre /error, el 404 sale como 401.
#
# El sintoma real que provoco: la app mobile llamo a un endpoint que el backend
# deployado todavia no tenia, recibio "Falta el token, o no es valido", y cerro
# la sesion sola aunque el token estuviera perfecto.
EsperarCodigo { Invoke-RestMethod -Uri "$base/esta-ruta-no-existe" -Headers $franco } 404 `
    "una ruta inexistente da 404 y no 401, aun con token valido"

# ---------------------------------------------------------------------------
Titulo "0.2 Una cuenta nueva, sola en su grupo"

# El estado de cualquiera que baje la app desde la App Store: se registra sin
# codigo, cae en un grupo propio, y la app funciona como registro personal.
# Queda creada entre corridas, igual que Franco y Ella.
$sesionSola = RegistrarOEntrar "Sola" "sola@local" $PASSWORD
$sola = @{ Authorization = "Bearer $($sesionSola.token)" }

$grupoSola = Invoke-RestMethod -Uri "$base/grupo" -Headers $sola
Chequear (($grupoSola.integrantes | Measure-Object).Count -eq 1) `
    "quien se registra sin codigo queda sola en su grupo"
Chequear ($grupoSola.id -ne $grupoFranco.id) `
    "y NO cae en el grupo de otra pareja"

$resumenSola = Invoke-RestMethod -Uri "$base/gastos/resumen?mes=2026-09" -Headers $sola
Chequear ($resumenSola.tienePareja -eq $false) "su resumen dice tienePareja = false"
$resumenConPareja = Invoke-RestMethod -Uri "$base/gastos/resumen?mes=2026-09" -Headers $franco
Chequear ($resumenConPareja.tienePareja -eq $true) "el de Franco dice tienePareja = true"

EsperarRegla { Crear $sola @{
    monto = 1000; categoriaId = $CAFE; fecha = "2026-09-06"
    descripcion = "cafe"; tipo = "COMPARTIDO" } } `
    "la otra persona tiene que estar en tu grupo" "sin pareja, un COMPARTIDO se rechaza"

EsperarRegla { Invoke-RestMethod -Uri "$base/pozos" -Method Post -Headers $sola `
    -ContentType "application/json" -Body (@{ nombre = "Viaje sola" } | ConvertTo-Json) } `
    "la otra persona tiene que estar en tu grupo" "sin pareja, no se puede abrir una vaquita"

# Un PERSONAL entra igual: sola, la app es un registro personal completo.
$personalDeSola = Crear $sola @{
    monto = 1000; categoriaId = $CAFE; fecha = "2026-09-06"
    descripcion = "cafe de la esquina"; tipo = "PERSONAL"; esHormiga = $true
}
Chequear ($personalDeSola.tipo -eq "PERSONAL") "sin pareja, un PERSONAL entra normal"
Invoke-RestMethod -Uri "$base/gastos/$($personalDeSola.id)" -Method Delete -Headers $sola | Out-Null

# ---------------------------------------------------------------------------
Titulo "1. Ella carga un gasto PERSONAL marcado como hormiga"

$personalDeElla = Crear $ella @{
    monto       = 3008
    categoriaId = $COMIDA
    fecha       = "2026-09-06"
    descripcion = "desayuno facultad"
    tipo        = "PERSONAL"
    esHormiga   = $true
}
Chequear ($personalDeElla.montoPagador -eq 3008)   "montoPagador = monto en un personal"
Chequear ($personalDeElla.deudaGenerada -eq 0)     "un gasto personal no genera deuda"
Chequear ($personalDeElla.esHormiga -eq $true)     "quedo marcado como hormiga"
Chequear ($personalDeElla.pagadoPor.nombre -eq "Ella") "lo pago Ella"
Chequear ($null -eq $personalDeElla.pagadoPor.email)   "la respuesta NO expone el email"

# ---------------------------------------------------------------------------
Titulo "2. Franco carga un COMPARTIDO de 10.01 al 50/50 (el centavo impar)"

$compartido = Crear $franco @{
    monto       = 10.01
    categoriaId = $CAFE
    fecha       = "2026-09-06"
    descripcion = "cafe con ella"
    tipo        = "COMPARTIDO"
    esHormiga   = $false
}
Chequear ($compartido.montoPagador -eq 5.01)  "el pagador absorbe el centavo (5.01)"
Chequear ($compartido.deudaGenerada -eq 5.00) "el otro debe 5.00"
Chequear (($compartido.montoPagador + $compartido.deudaGenerada) -eq 10.01) `
    "las partes suman el total exacto"

# ---------------------------------------------------------------------------
Titulo "3. Visibilidad: Franco NO puede ver el gasto personal de Ella"

$listaFranco = Invoke-RestMethod -Uri "$base/gastos?mes=2026-09" -Headers $franco
$ids = $listaFranco | ForEach-Object { $_.id }
Chequear ($ids -contains $compartido.id)      "Franco ve el compartido"
Chequear (-not ($ids -contains $personalDeElla.id)) "Franco NO ve el personal de Ella"

EsperarCodigo { Invoke-RestMethod -Uri "$base/gastos/$($personalDeElla.id)" -Method Delete -Headers $franco } `
    404 "pedirlo por id devuelve 404, no 403 (no confirma que existe)"

# GET /gastos/{id}: lo usa la pantalla de edicion, que necesita el gasto y su
# `version` al dia. Pasa por la misma regla de visibilidad que el listado, y eso
# es justamente lo que hay que verificar: un endpoint nuevo es un lugar nuevo
# donde alguien puede olvidarse el WHERE.
$traido = Invoke-RestMethod -Uri "$base/gastos/$($compartido.id)" -Headers $franco
Chequear ($traido.id -eq $compartido.id)        "traer un gasto por id devuelve ese gasto"
Chequear ($null -ne $traido.version)            "y trae la version, que es para lo que sirve"
Chequear ($null -eq $traido.pagadoPor.email)    "traer por id tampoco expone el email"

EsperarCodigo { Invoke-RestMethod -Uri "$base/gastos/$($personalDeElla.id)" -Headers $franco } `
    404 "traer por id el personal de la otra persona da 404"

# ---------------------------------------------------------------------------
Titulo "4. Visibilidad: Ella ve los dos"

$listaElla = Invoke-RestMethod -Uri "$base/gastos?mes=2026-09" -Headers $ella
$idsElla = $listaElla | ForEach-Object { $_.id }
Chequear ($idsElla -contains $compartido.id)      "Ella ve el compartido"
Chequear ($idsElla -contains $personalDeElla.id)  "Ella ve su propio personal"

# ---------------------------------------------------------------------------
Titulo "5. Filtros"

$soloCafe = Invoke-RestMethod -Uri "$base/gastos?mes=2026-09&categoria=$CAFE" -Headers $ella
Chequear (($soloCafe | Measure-Object).Count -eq 1) "filtro por categoria deja 1"

$soloFranco = Invoke-RestMethod -Uri "$base/gastos?mes=2026-09&pagadoPor=$FRANCO_ID" -Headers $ella
Chequear (($soloFranco | Measure-Object).Count -eq 1) "filtro por pagador deja 1"

$otroMes = Invoke-RestMethod -Uri "$base/gastos?mes=2026-01" -Headers $ella
Chequear (($otroMes | Measure-Object).Count -eq 0) "otro mes viene vacio"

# ---------------------------------------------------------------------------
Titulo "6. Validaciones"

EsperarValidacion { Crear $franco @{ monto = -500; categoriaId = $CAFE; fecha = "2026-09-06"
                                     descripcion = "invalido"; tipo = "PERSONAL" } } `
    "monto" "monto negativo rechazado por @Positive"

EsperarValidacion { Crear $franco @{ monto = 100; categoriaId = $CAFE; fecha = "2030-01-01"
                                     descripcion = "invalido"; tipo = "PERSONAL" } } `
    "fecha" "fecha futura rechazada por @PastOrPresent"

# La descripcion es opcional desde la v1.0: sin ella el gasto entra, y vuelve
# null (no ""). Se borra enseguida para no ensuciar los totales de abajo.
$sinDescripcion = Crear $franco @{ monto = 100; categoriaId = $CAFE; fecha = "2026-09-06"
                                   tipo = "PERSONAL" }
Chequear ($null -eq $sinDescripcion.descripcion) "sin descripcion el gasto entra, y la descripcion vuelve null"
$soloEspacios = Crear $franco @{ monto = 100; categoriaId = $CAFE; fecha = "2026-09-06"
                                 descripcion = "   "; tipo = "PERSONAL" }
Chequear ($null -eq $soloEspacios.descripcion) "solo espacios tambien se guarda como null"
Invoke-RestMethod -Uri "$base/gastos/$($sinDescripcion.id)" -Method Delete -Headers $franco | Out-Null
Invoke-RestMethod -Uri "$base/gastos/$($soloEspacios.id)" -Method Delete -Headers $franco | Out-Null

EsperarValidacion { Crear $franco @{ monto = 100; categoriaId = $CAFE; fecha = "2026-09-06"
                                     descripcion = "x"; tipo = "COMPARTIDO"
                                     porcentajePagador = 150 } } `
    "porcentajePagador" "porcentaje mayor a 100 rechazado por @Max"

# El id es un ObjectId (24 caracteres hexadecimales), no un numero. Este es
# valido en forma pero no existe: si mandaramos "999" el driver lo rechazaria
# por formato y tendriamos un 400 de conversion en vez del error de negocio que
# queremos probar.
EsperarRegla { Crear $franco @{ monto = 100; categoriaId = "000000000000000000000000"; fecha = "2026-09-06"
                                descripcion = "x"; tipo = "PERSONAL" } } `
    "No existe la categor" "categoria inexistente rechazada por el servicio"

EsperarRegla { Crear $franco @{ monto = 100; categoriaId = $CAFE; fecha = "2026-09-06"
                                descripcion = "x"; tipo = "PERSONAL"; pagadoPorId = $ELLA_ID } } `
    "solo lo puede cargar quien lo pag" "no se puede cargar un personal a nombre de otro"

EsperarCodigo { Invoke-RestMethod -Uri "$base/gastos?mes=2026-09" } `
    401 "una request sin token da 401"

# Sin esHormiga en el cuerpo: tiene que crearse igual, con esHormiga=false.
$sinFlag = Crear $franco @{ monto = 100; categoriaId = $CAFE; fecha = "2026-09-06"
                            descripcion = "sin el flag"; tipo = "PERSONAL" }
Chequear ($sinFlag.esHormiga -eq $false) "omitir esHormiga no rompe el parseo y default a false"
Invoke-RestMethod -Uri "$base/gastos/$($sinFlag.id)" -Method Delete -Headers $franco | Out-Null

# ---------------------------------------------------------------------------
Titulo "7. Resumen y saldo"

# Un tercer gasto para que los numeros no sean triviales:
# Franco paga un uber compartido de 1000 al 50/50, marcado como hormiga.
$uber = Crear $franco @{
    monto = 1000; categoriaId = $UBER; fecha = "2026-09-06"
    descripcion = "uber a lo de mi vieja"; tipo = "COMPARTIDO"
    porcentajePagador = 50; esHormiga = $true
}

# Estado en este punto:
#   personal de Ella   3008.00  hormiga   (pago Ella)
#   cafe compartido      10.01            (pago Franco, su parte 5.01)
#   uber compartido    1000.00  hormiga   (pago Franco, su parte 500.00)
#
# Parte de Franco = 5.01 + 500.00 = 505.01, de la cual 500.00 es hormiga.
# El personal de Ella no lo ve, y aunque lo viera su parte seria cero.
$resumenFranco = Invoke-RestMethod -Uri "$base/gastos/resumen?mes=2026-09" -Headers $franco
Chequear ($resumenFranco.total -eq 505.01)        "el total de Franco es su parte, no el total del grupo"
Chequear ($resumenFranco.totalHormiga -eq 500.00) "el hormiga de Franco es solo el uber"

# Parte de Ella = 3008.00 + 5.00 + 500.00 = 3513.00, de la cual 3508.00 es hormiga.
$resumenElla = Invoke-RestMethod -Uri "$base/gastos/resumen?mes=2026-09" -Headers $ella
Chequear ($resumenElla.total -eq 3513.00)         "el total de Ella incluye su parte de los compartidos"
Chequear ($resumenElla.totalHormiga -eq 3508.00)  "el hormiga de Ella suma su personal y su parte del uber"
Chequear ($resumenElla.animo -eq "TRANQUILA")     "sin datos del mes anterior la nutria no juzga"

$cats = $resumenElla.porCategoria
Chequear (($cats | Measure-Object).Count -eq 3)   "el desglose trae las tres categorias"
Chequear ($cats[0].total -ge $cats[1].total)      "el desglose viene ordenado de mayor a menor"

# Saldo: Franco pago los dos compartidos, asi que Ella le debe 5.00 + 500.00.
$saldoFranco = Invoke-RestMethod -Uri "$base/saldo?mes=2026-09" -Headers $franco
Chequear ($saldoFranco.monto -eq 505.00)          "Ella le debe 505.00 a Franco"
Chequear ($saldoFranco.deudorNombre -eq "Ella")   "el deudor es Ella"
Chequear ($saldoFranco.aFavorMio -eq 505.00)      "visto por Franco, el saldo es positivo"

# El mismo saldo visto del otro lado tiene que dar lo mismo, con el signo dado vuelta.
$saldoElla = Invoke-RestMethod -Uri "$base/saldo?mes=2026-09" -Headers $ella
Chequear ($saldoElla.monto -eq 505.00)            "los dos ven el mismo monto"
Chequear ($saldoElla.aFavorMio -eq -505.00)       "visto por Ella, el saldo es negativo"

# Un mes sin gastos: todo en cero, y sin deudor ni acreedor.
$saldoVacio = Invoke-RestMethod -Uri "$base/saldo?mes=2026-01" -Headers $franco
Chequear ($saldoVacio.monto -eq 0)                "un mes sin gastos da saldo cero"
Chequear ($null -eq $saldoVacio.deudorId)         "sin deuda no hay deudor"

Invoke-RestMethod -Uri "$base/gastos/$($uber.id)" -Method Delete -Headers $franco | Out-Null

# ---------------------------------------------------------------------------
Titulo "8. Edicion y bloqueo optimista"

$editado = Invoke-RestMethod -Uri "$base/gastos/$($compartido.id)" -Method Put -Headers $franco `
    -ContentType "application/json" -Body (@{
        monto = 20.00; categoriaId = $CAFE; fecha = "2026-09-06"
        descripcion = "cafe con ella (corregido)"; tipo = "COMPARTIDO"
        porcentajePagador = 50; esHormiga = $false; version = $compartido.version
    } | ConvertTo-Json)
Chequear ($editado.monto -eq 20.00)        "el monto se actualizo"
Chequear ($editado.montoPagador -eq 10.00) "el reparto se recalculo"
Chequear ($editado.version -gt $compartido.version) "la version se incremento"

EsperarCodigo { Invoke-RestMethod -Uri "$base/gastos/$($compartido.id)" -Method Put -Headers $franco `
    -ContentType "application/json" -Body (@{
        monto = 99.00; categoriaId = $CAFE; fecha = "2026-09-06"
        descripcion = "version vieja"; tipo = "COMPARTIDO"
        version = $compartido.version
    } | ConvertTo-Json) } `
    409 "editar con una version vieja da 409 Conflict"

# --- GET por id, que es lo que usa la pantalla de edicion -------------------
$traido = Invoke-RestMethod -Uri "$base/gastos/$($compartido.id)" -Headers $ella
Chequear ($traido.id -eq $compartido.id) "GET /gastos/{id} trae el gasto"
Chequear ($traido.porcentajePagador -eq 50) `
    "y trae porcentajePagador derivado, para que la app no divida plata"

# Un PERSONAL no tiene reparto que mostrar.
$personalTraido = Invoke-RestMethod -Uri "$base/gastos/$($personalDeElla.id)" -Headers $ella
Chequear ($null -eq $personalTraido.porcentajePagador) "un PERSONAL no trae porcentaje"

# La regla de visibilidad tambien aplica al GET por id: el personal de Ella no
# lo puede traer Franco, y da 404 y no 403 -- un 403 confirmaria que existe.
EsperarCodigo { Invoke-RestMethod -Uri "$base/gastos/$($personalDeElla.id)" -Headers $franco } `
    404 "el gasto personal de otra persona da 404 por id, no 403"

EsperarCodigo { Invoke-RestMethod -Uri "$base/gastos/000000000000000000000000" -Headers $franco } `
    404 "un id que no existe da 404"

# ---------------------------------------------------------------------------
Titulo "9. Idempotencia de la cola offline"

# EL ESCENARIO QUE ESTO CUBRE: la app guarda el gasto en una cola local y lo
# reintenta hasta que entra. Con mala senial puede pasar que el POST llegue, el
# servidor escriba el gasto, y la respuesta se pierda de vuelta. El telefono no
# tiene forma de distinguir eso de "no llego", asi que reintenta.
#
# Sin clienteId, ese reintento crea un SEGUNDO gasto. Y un gasto duplicado no se
# ve como un error: se ve como un total del mes equivocado, en silencio.
$clave = "smoke-$([guid]::NewGuid().ToString('N').Substring(0,12))"

$primero = Crear $franco @{
    monto = 3500.00; categoriaId = $CAFE; fecha = "2026-09-06"
    descripcion = "cafe con mala senial"; tipo = "PERSONAL"
    esHormiga = $true; clienteId = $clave
}
Chequear ($primero.id -is [string]) "el primer envio crea el gasto"

# El reintento, byte por byte el mismo.
$reintento = Crear $franco @{
    monto = 3500.00; categoriaId = $CAFE; fecha = "2026-09-06"
    descripcion = "cafe con mala senial"; tipo = "PERSONAL"
    esHormiga = $true; clienteId = $clave
}
Chequear ($reintento.id -eq $primero.id) `
    "el reintento con la misma clave devuelve EL MISMO gasto, no uno nuevo"

$conEsaDescripcion = (Invoke-RestMethod -Uri "$base/gastos?mes=2026-09" -Headers $franco) |
    Where-Object { $_.descripcion -eq "cafe con mala senial" }
Chequear (($conEsaDescripcion | Measure-Object).Count -eq 1) `
    "quedo un solo gasto cargado, no dos"

# Sin clave no hay indice que violar, asi que dos envios identicos SI crean dos
# gastos. Es el comportamiento correcto: un cliente que no manda la clave (curl,
# el atajo de iOS) no puede pedir la garantia.
$suelto1 = Crear $franco @{
    monto = 111.00; categoriaId = $CAFE; fecha = "2026-09-06"
    descripcion = "sin clave"; tipo = "PERSONAL"
}
$suelto2 = Crear $franco @{
    monto = 111.00; categoriaId = $CAFE; fecha = "2026-09-06"
    descripcion = "sin clave"; tipo = "PERSONAL"
}
Chequear ($suelto1.id -ne $suelto2.id) "sin clienteId, dos envios iguales son dos gastos"

# La clave es unica POR GRUPO, no global. Ella esta en el mismo grupo que Franco,
# asi que su reintento con la misma clave tiene que resolver al mismo gasto.
$deElla = Crear $ella @{
    monto = 3500.00; categoriaId = $CAFE; fecha = "2026-09-06"
    descripcion = "otra cosa"; tipo = "COMPARTIDO"; clienteId = $clave
}
Chequear ($deElla.id -eq $primero.id) `
    "la clave es del grupo: el mismo clienteId resuelve al gasto que ya estaba"

Invoke-RestMethod -Uri "$base/gastos/$($primero.id)" -Method Delete -Headers $franco | Out-Null
Invoke-RestMethod -Uri "$base/gastos/$($suelto1.id)" -Method Delete -Headers $franco | Out-Null
Invoke-RestMethod -Uri "$base/gastos/$($suelto2.id)" -Method Delete -Headers $franco | Out-Null

# ---------------------------------------------------------------------------
Titulo "10. La vaquita"

# Sin pozo abierto, /pozos/activo devuelve 204 y no 404: no tener vaquita es un
# estado normal de la app, no un error.
EsperarSinContenido "$base/pozos/activo" $franco "sin vaquita abierta, /pozos/activo da 204 y no 404"

# Se crea SIN fechas a proposito: asi `vigente` no depende del dia en que se
# corra el script. El rango de fechas lo cubren los tests puros de PozoTest.
$pozo = Invoke-RestMethod -Uri "$base/pozos" -Method Post -Headers $franco `
    -ContentType "application/json" -Body (@{
        nombre = "Bariloche"; objetivo = 800000.00
    } | ConvertTo-Json)

Chequear ($pozo.id -is [string] -and $pozo.id.Length -eq 24) "la vaquita se crea con un ObjectId"
Chequear ($pozo.estado -eq "ABIERTO")   "nace abierta"
Chequear ($pozo.aportado -eq 0)         "arranca sin plata"
Chequear ($pozo.restante -eq 0)         "y sin nada gastado"
Chequear ($pozo.vigente -eq $true)      "una vaquita abierta sin fechas esta vigente"
Chequear (($pozo.porPersona | Measure-Object).Count -eq 2) `
    "porPersona lista a los dos integrantes, incluso al que no aporto"

EsperarRegla { Invoke-RestMethod -Uri "$base/pozos" -Method Post -Headers $ella `
    -ContentType "application/json" -Body (@{ nombre = "Otra" } | ConvertTo-Json) } `
    "Ya hay una vaquita abierta" "no se puede abrir una segunda vaquita en el grupo"

# --- aportes ---------------------------------------------------------------

# Un aporte mal cargado ya NO se corrige con un asiento en contra: se edita o
# se borra tocando la fila, igual que un ingreso de "Mi Plata" (seccion 2.3c).
# El monto tambien dejo de admitir negativo o cero -- @Positive, mismo
# precedente que RegistrarIngresoRequest. Ver docs/vaquita.md, seccion 12.
EsperarValidacion { Invoke-RestMethod -Uri "$base/pozos/$($pozo.id)/aportes" -Method Post -Headers $franco `
    -ContentType "application/json" -Body (@{ monto = 0 } | ConvertTo-Json) } `
    "monto" "un aporte de cero se rechaza por Bean Validation"

EsperarValidacion { Invoke-RestMethod -Uri "$base/pozos/$($pozo.id)/aportes" -Method Post -Headers $franco `
    -ContentType "application/json" -Body (@{ monto = -90000 } | ConvertTo-Json) } `
    "monto" "un aporte negativo tambien se rechaza: ya no es la forma de corregir uno mal cargado"

# Franco se equivoca de digitos: tipea 4.000.000 en vez de 400.000.
$conTypo = Invoke-RestMethod -Uri "$base/pozos/$($pozo.id)/aportes" -Method Post -Headers $franco `
    -ContentType "application/json" -Body (@{ monto = 4000000.00 } | ConvertTo-Json)
$idAporteFranco = $conTypo.aportes[0].id
Chequear ($null -ne $idAporteFranco) "el aporte que vuelve trae id: hace falta para poder editarlo o borrarlo"

# El dueno es quien aporto, no quien pide: Ella no puede tocar el aporte de
# Franco, ni para editarlo ni para borrarlo. Bug real encontrado contra Mongo
# de verdad esta sesion (docs/vaquita.md, seccion 12): un filtro sin
# $elemMatch podia dejar pasar justo esto.
EsperarCodigo { Invoke-RestMethod -Uri "$base/pozos/$($pozo.id)/aportes/$idAporteFranco" -Method Put -Headers $ella `
    -ContentType "application/json" -Body (@{ monto = 1.00 } | ConvertTo-Json) } 404 `
    "editar el aporte de la otra persona da 404"

EsperarCodigo { Invoke-RestMethod -Uri "$base/pozos/$($pozo.id)/aportes/$idAporteFranco" -Method Delete -Headers $ella } 404 `
    "borrar el aporte de la otra persona da 404"

EsperarValidacion { Invoke-RestMethod -Uri "$base/pozos/$($pozo.id)/aportes/$idAporteFranco" -Method Put -Headers $franco `
    -ContentType "application/json" -Body (@{ monto = 0 } | ConvertTo-Json) } `
    "monto" "editar un aporte a cero se rechaza, mismo @Positive que crearlo"

$pozo = Invoke-RestMethod -Uri "$base/pozos/$($pozo.id)/aportes/$idAporteFranco" -Method Put -Headers $franco `
    -ContentType "application/json" -Body (@{ monto = 400000.00 } | ConvertTo-Json)
Chequear ($pozo.aportado -eq 400000.00) "franco corrige el typo tocando la fila"
Chequear (($pozo.aportes | Measure-Object).Count -eq 1) "editar no deja un asiento de mas, corrige el que estaba"

$pozo = Invoke-RestMethod -Uri "$base/pozos/$($pozo.id)/aportes" -Method Post -Headers $ella `
    -ContentType "application/json" -Body (@{ monto = 400000.00 } | ConvertTo-Json)
Chequear ($pozo.aportado -eq 800000.00) "los dos aportes suman"
Chequear ($pozo.restante -eq 800000.00) "sin gastos, el restante es todo lo aportado"

# El aporte se registra SIEMPRE a nombre de quien hace la request: no hay forma
# de anotar plata a nombre de la otra persona.
$deElla = $pozo.porPersona | Where-Object { $_.usuarioId -eq $ELLA_ID }
Chequear ($deElla.total -eq 400000.00) "cada aporte queda a nombre de quien lo hizo"

# Ella toca dos veces el boton de aportar sin querer y le queda un aporte
# duplicado. A diferencia del typo de arriba (donde el aporte debia EXISTIR
# con otro valor), este no debia existir: la correccion es borrarlo, no
# editarlo.
$conDuplicado = Invoke-RestMethod -Uri "$base/pozos/$($pozo.id)/aportes" -Method Post -Headers $ella `
    -ContentType "application/json" -Body (@{ monto = 50000.00 } | ConvertTo-Json)
$idAporteDuplicado = ($conDuplicado.aportes | Where-Object { $_.usuario.id -eq $ELLA_ID } | Select-Object -Last 1).id
Chequear ($conDuplicado.aportado -eq 850000.00) "el aporte duplicado entra igual, nada lo distingue de uno real"

# Chequeo de dueno en la otra direccion: ahora es Franco quien no puede borrar
# un aporte de Ella.
EsperarCodigo { Invoke-RestMethod -Uri "$base/pozos/$($pozo.id)/aportes/$idAporteDuplicado" -Method Delete -Headers $franco } 404 `
    "borrar el aporte de la otra persona da 404, tambien en este sentido"

$pozo = Invoke-RestMethod -Uri "$base/pozos/$($pozo.id)/aportes/$idAporteDuplicado" -Method Delete -Headers $ella
Chequear ($pozo.aportado -eq 800000.00) "ella borra su propio aporte duplicado"
Chequear (($pozo.aportes | Measure-Object).Count -eq 2) "y quedan los dos aportes reales, ni uno de mas"

EsperarCodigo { Invoke-RestMethod -Uri "$base/pozos/$($pozo.id)/aportes/$idAporteDuplicado" -Method Delete -Headers $ella } 404 `
    "borrar el mismo aporte de nuevo da 404: ya no existe"

# --- el saldo y la nutria ANTES de tocar la vaquita -------------------------
$saldoAntes   = Invoke-RestMethod -Uri "$base/saldo?mes=2026-09" -Headers $franco
$resumenAntes = Invoke-RestMethod -Uri "$base/gastos/resumen?mes=2026-09" -Headers $franco

# --- un gasto que sale del pozo --------------------------------------------
$gastoPozo = Crear $franco @{
    monto = 120000.00; categoriaId = $COMIDA; fecha = "2026-09-06"
    descripcion = "cena en el centro civico"; tipo = "COMPARTIDO"
    esHormiga = $true; pozoId = $pozo.id
}
Chequear ($gastoPozo.pozoId -eq $pozo.id)        "el gasto queda marcado con la vaquita"
Chequear ($gastoPozo.montoPagador -eq 60000.00)  "un gasto del pozo es mitad y mitad por construccion"

$pozo = Invoke-RestMethod -Uri "$base/pozos/activo" -Headers $ella
Chequear ($pozo.gastado -eq 120000.00)  "el gasto se descuenta de la vaquita"
Chequear ($pozo.restante -eq 680000.00) "restante = aportado - gastado"

# gastadoPorPersona: quien gasto cuanto DEL POZO, puramente informativo (no
# genera ninguna deuda -- eso lo siguen probando los dos chequeos de abajo).
$gastoDeFranco = $pozo.gastadoPorPersona | Where-Object { $_.usuarioId -eq $FRANCO_ID }
$gastoDeElla   = $pozo.gastadoPorPersona | Where-Object { $_.usuarioId -eq $ELLA_ID }
Chequear (($pozo.gastadoPorPersona | Measure-Object).Count -eq 2) `
    "gastadoPorPersona lista a los dos integrantes, incluso al que no gasto nada del pozo"
Chequear ($gastoDeFranco.total -eq 120000.00) "quien cargo el gasto del pozo aparece con lo que gasto"
Chequear ($gastoDeElla.total -eq 0)           "y el otro integrante aparece con cero, no desaparece"

# ESTOS DOS SON LOS CHEQUEOS QUE IMPORTAN DE TODA LA SECCION.
#
# Un viaje no ensucia el mes: no genera deuda entre ellos (la plata ya se
# repartio al aportar) y no cuenta como gasto hormiga (es plata que se ahorro a
# proposito). Si alguno de estos dos falla, se rompio `sinPozo()` en
# GastoConsultasImpl y la nutria va a estar preocupada durante las vacaciones.
$saldoDespues   = Invoke-RestMethod -Uri "$base/saldo?mes=2026-09" -Headers $franco
$resumenDespues = Invoke-RestMethod -Uri "$base/gastos/resumen?mes=2026-09" -Headers $franco

Chequear ($saldoDespues.aFavorMio -eq $saldoAntes.aFavorMio) `
    "un gasto de la vaquita NO mueve el saldo entre ellos"
Chequear ($resumenDespues.totalHormiga -eq $resumenAntes.totalHormiga) `
    "un gasto de la vaquita NO cuenta para el total hormiga del mes"

$delMes = Invoke-RestMethod -Uri "$base/gastos?mes=2026-09" -Headers $franco
Chequear (($delMes | Where-Object { $_.id -eq $gastoPozo.id } | Measure-Object).Count -eq 0) `
    "el gasto de la vaquita no aparece en el listado del mes"

$delPozo = Invoke-RestMethod -Uri "$base/pozos/$($pozo.id)/gastos" -Headers $ella
Chequear (($delPozo | Measure-Object).Count -eq 1) "pero si en el listado del viaje"
Chequear ($delPozo[0].descripcion -eq "cena en el centro civico") "y lo ven los dos"

# --- las reglas ------------------------------------------------------------
# Se RECHAZA en vez de corregirse: promover el gasto a COMPARTIDO en silencio
# publicaria un gasto que su duenio marco como privado. El caso real es un
# regalo sorpresa cargado con la vaquita puesta sin querer.
EsperarRegla { Crear $franco @{
        monto = 5000.00; categoriaId = $CAFE; fecha = "2026-09-06"
        descripcion = "regalo"; tipo = "PERSONAL"; pozoId = $pozo.id
    } } "no puede salir de la vaquita" "un gasto PERSONAL no puede salir de la vaquita"

EsperarRegla { Crear $franco @{
        monto = 5000.00; categoriaId = $CAFE; fecha = "2026-09-06"
        descripcion = "pozo inventado"; tipo = "COMPARTIDO"
        pozoId = "000000000000000000000000"
    } } "No existe la vaquita" "no se puede cargar a una vaquita que no existe"

# --- sobregiro: se permite, y queda en rojo --------------------------------
# Bloquear una carga parada en el mostrador es el pecado capital de esta app.
# Validar no es lo mismo que bloquear.
$gastoPasado = Crear $franco @{
    monto = 700000.00; categoriaId = $COMIDA; fecha = "2026-09-06"
    descripcion = "el hotel"; tipo = "COMPARTIDO"; pozoId = $pozo.id
}
$pozo = Invoke-RestMethod -Uri "$base/pozos/activo" -Headers $franco
Chequear ($pozo.restante -lt 0) "gastar mas de lo aportado se permite y deja el pozo en rojo"

# --- cierre ----------------------------------------------------------------
# gastoPozo se deja vivo a proposito: abajo se prueba que un gasto de una
# vaquita CERRADA todavia se puede corregir.
Invoke-RestMethod -Uri "$base/gastos/$($gastoPasado.id)" -Method Delete -Headers $franco | Out-Null

$cerrado = Invoke-RestMethod -Uri "$base/pozos/$($pozo.id)/cerrar" -Method Post -Headers $franco
Chequear ($cerrado.estado -eq "CERRADO") "la vaquita se cierra"
Chequear ($cerrado.vigente -eq $false)   "y deja de estar vigente"

EsperarCodigo { Invoke-RestMethod -Uri "$base/pozos/$($pozo.id)/cerrar" -Method Post -Headers $franco } `
    404 "cerrar dos veces no es un exito silencioso"

EsperarCodigo { Invoke-RestMethod -Uri "$base/pozos/$($pozo.id)/aportes" -Method Post -Headers $ella `
    -ContentType "application/json" -Body (@{ monto = 1000.00 } | ConvertTo-Json) } `
    404 "no se puede aportar a una vaquita cerrada"

EsperarRegla { Crear $franco @{
        monto = 5000.00; categoriaId = $CAFE; fecha = "2026-09-06"
        descripcion = "tarde"; tipo = "COMPARTIDO"; pozoId = $pozo.id
    } } "cerrada" "no se puede cargar un gasto a una vaquita cerrada"

EsperarSinContenido "$base/pozos/activo" $ella "despues de cerrarla, no hay vaquita activa"

# GET /pozos es lo que hace alcanzable una vaquita cerrada. Sin el, sus gastos
# quedaban sin ninguna pantalla desde la cual llegar: no salen en la lista del
# mes y /pozos/activo deja de devolverla.
$todos = Invoke-RestMethod -Uri "$base/pozos" -Headers $ella
Chequear ((($todos | Where-Object { $_.id -eq $pozo.id }) | Measure-Object).Count -eq 1) `
    "GET /pozos incluye las vaquitas cerradas"

$delCerrado = Invoke-RestMethod -Uri "$base/pozos/$($pozo.id)/gastos" -Headers $ella
Chequear (($delCerrado | Measure-Object).Count -ge 1) "y se pueden listar sus gastos"

# CERRAR CONGELA LA VAQUITA, NO LOS GASTOS QUE YA TENIA.
#
# La diferencia importa: volviendo del viaje cierran la vaquita, y recien ahi
# alguien mira la lista y ve que una cena quedo con un cero de mas. Si el cierre
# bloqueara tambien las correcciones, ese error seria permanente.
$corregido = Invoke-RestMethod -Uri "$base/gastos/$($gastoPozo.id)" -Method Put -Headers $franco `
    -ContentType "application/json" -Body (@{
        monto = 130000.00; categoriaId = $COMIDA; fecha = "2026-09-06"
        descripcion = "cena en el centro civico (corregida)"; tipo = "COMPARTIDO"
        esHormiga = $true; pozoId = $pozo.id; version = $gastoPozo.version
    } | ConvertTo-Json)
Chequear ($corregido.monto -eq 130000.00) "un gasto de una vaquita CERRADA se puede corregir"
Chequear ($corregido.pozoId -eq $pozo.id) "y sigue perteneciendo a esa vaquita"

Invoke-RestMethod -Uri "$base/gastos/$($gastoPozo.id)" -Method Delete -Headers $franco | Out-Null

# Nota: el documento del pozo CERRADO queda en la base, igual que los usuarios.
# No molesta para volver a correr el script, porque el indice unico solo aplica
# a los ABIERTOS. No hay endpoint para borrar un pozo, y es a proposito: son
# registros de plata.

# ---------------------------------------------------------------------------
Titulo "11. Limpieza"

Invoke-RestMethod -Uri "$base/gastos/$($compartido.id)" -Method Delete -Headers $franco | Out-Null
Invoke-RestMethod -Uri "$base/gastos/$($personalDeElla.id)" -Method Delete -Headers $ella | Out-Null

$quedan = Invoke-RestMethod -Uri "$base/gastos?mes=2026-09" -Headers $ella
Chequear (($quedan | Measure-Object).Count -eq 0) "quedo todo limpio"

# ---------------------------------------------------------------------------
Titulo "12. Borrar la cuenta"

# Con una pareja descartable y no con Franco y Ella: al final del bloque las dos
# cuentas estan borradas, asi que cada corrida arranca de cero y el script se
# limpia solo. De paso prueba los DOS caminos: la primera en irse deja a alguien
# (se anonimiza), la segunda es la ultima del grupo (se va todo).
$sesionBorra = RegistrarOEntrar "Borra" "borra@local" $PASSWORD
$sesionQueda = RegistrarOEntrar "Queda" "queda@local" $PASSWORD
JuntarEnGrupo "queda@local" "borra@local"
$borra = @{ Authorization = "Bearer $($sesionBorra.token)" }
$queda = @{ Authorization = "Bearer $($sesionQueda.token)" }
$BORRA_ID = $sesionBorra.usuario.id
$GRUPO_BQ = (Invoke-RestMethod -Uri "$base/grupo" -Headers $borra).id

$personalDeBorra = Crear $borra @{ monto = 5000; categoriaId = $CAFE; fecha = "2026-09-06"
    descripcion = "regalo sorpresa"; tipo = "PERSONAL" }
$compartidoDeBorra = Crear $borra @{ monto = 8000; categoriaId = $COMIDA; fecha = "2026-09-06"
    descripcion = "cena del sabado"; tipo = "COMPARTIDO"; porcentajePagador = 50 }
$pozoBQ = Invoke-RestMethod -Uri "$base/pozos" -Method Post -Headers $queda `
    -ContentType "application/json" -Body (@{ nombre = "Escapada" } | ConvertTo-Json)
# Dos aportes de Borra: el anonimizado tiene que tocar TODOS, no solo el primero.
# Es justo lo que distingue $[a] con arrayFilters de un $ a secas.
1..2 | ForEach-Object {
    Invoke-RestMethod -Uri "$base/pozos/$($pozoBQ.id)/aportes" -Method Post -Headers $borra `
        -ContentType "application/json" -Body (@{ monto = 1000 } | ConvertTo-Json) | Out-Null
}

$cuerpoMal = @{ password = "no-es-mi-contrasena" } | ConvertTo-Json
EsperarRegla { Invoke-RestMethod -Uri "$base/auth/borrar-cuenta" -Method Post -Headers $borra `
    -ContentType "application/json" -Body $cuerpoMal } `
    "no es correcta" "con la contrasena equivocada no borra, y da 400 y no 401"

$r = Invoke-WebRequest -Uri "$base/auth/borrar-cuenta" -Method Post -Headers $borra -UseBasicParsing `
    -ContentType "application/json" -Body (@{ password = $PASSWORD } | ConvertTo-Json)
Chequear ($r.StatusCode -eq 204) "con la contrasena correcta borra la cuenta (204)"

EsperarCodigo { Invoke-RestMethod -Uri "$base/gastos?mes=2026-09" -Headers $borra } `
    401 "el token de la cuenta borrada deja de servir"
EsperarCodigo { Entrar "borra@local" $PASSWORD } 401 "y ya no se puede entrar"

Chequear ((Mongo "print(db.gasto.countDocuments({ 'pagadoPor.usuarioId': '$BORRA_ID', tipo: 'PERSONAL' }))") -eq "0") `
    "sus gastos personales se borraron de la base"

$compartidoVisto = Invoke-RestMethod -Uri "$base/gastos/$($compartidoDeBorra.id)" -Headers $queda
Chequear ($compartidoVisto.pagadoPor.nombre -eq "Cuenta eliminada") `
    "el compartido queda en el historial de Queda, con el nombre anonimizado"
Chequear ($compartidoVisto.descripcion -eq "cena del sabado") "y con su descripcion"

$pozoVisto = Invoke-RestMethod -Uri "$base/pozos/activo" -Headers $queda
Chequear ($pozoVisto.aportado -eq 2000) "sus aportes siguen sumando en la vaquita"
$filaAnonima = $pozoVisto.porPersona | Where-Object { $_.nombre -eq "Cuenta eliminada" }
Chequear ($filaAnonima.total -eq 2000) "y aparecen en el desglose como Cuenta eliminada"
Chequear ((Mongo "print(db.pozo.countDocuments({ 'aportes.usuario.nombre': 'Borra' }))") -eq "0") `
    "no quedo ningun aporte con su nombre (arrayFilters toco todos)"

$corregido = Invoke-RestMethod -Uri "$base/gastos/$($compartidoDeBorra.id)" -Method Put -Headers $queda `
    -ContentType "application/json" -Body (@{ monto = 7000; categoriaId = $COMIDA; fecha = "2026-09-06"
        descripcion = "cena del sabado"; tipo = "COMPARTIDO"; porcentajePagador = 50
        version = $compartidoVisto.version } | ConvertTo-Json)
Chequear ($corregido.monto -eq 7000) "Queda puede corregir un compartido que pago la cuenta borrada"

Chequear ((Invoke-RestMethod -Uri "$base/gastos/resumen?mes=2026-09" -Headers $queda).tienePareja -eq $false) `
    "Queda quedo sola: su resumen dice tienePareja = false"

# La ultima del grupo: se va todo.
$r = Invoke-WebRequest -Uri "$base/auth/borrar-cuenta" -Method Post -Headers $queda -UseBasicParsing `
    -ContentType "application/json" -Body (@{ password = $PASSWORD } | ConvertTo-Json)
Chequear ($r.StatusCode -eq 204) "la ultima integrante borra su cuenta"
Chequear ((Mongo "print(db.gasto.countDocuments({ grupo_id: '$GRUPO_BQ' }) + db.pozo.countDocuments({ grupo_id: '$GRUPO_BQ' }))") -eq "0") `
    "sin nadie en el grupo, no quedan ni gastos ni vaquitas"
Chequear ((Mongo "print(db.grupo.countDocuments({ _id: ObjectId('$GRUPO_BQ') }))") -eq "0") `
    "y el grupo tambien se borro"

# ---------------------------------------------------------------------------
Titulo "13. Olvide mi contrasena"

# El camino feliz necesita el codigo, que sale por mail (o al log, en local): lo
# cubren RecuperacionServicioTest y una prueba a mano. Lo que se prueba aca es lo
# que protege contra un atacante, contra el backend de verdad.
$pedidoExiste = Invoke-WebRequest "$base/auth/olvide-contrasena" -Method Post -UseBasicParsing `
    -ContentType "application/json" -Body (@{ email = "sola@local" } | ConvertTo-Json)
$pedidoNoExiste = Invoke-WebRequest "$base/auth/olvide-contrasena" -Method Post -UseBasicParsing `
    -ContentType "application/json" -Body (@{ email = "nadie-nunca@local" } | ConvertTo-Json)
Chequear ($pedidoExiste.StatusCode -eq 204 -and $pedidoNoExiste.StatusCode -eq 204) `
    "pedir el codigo responde 204 exista o no la cuenta: no revela quien la tiene"

$errorCuentaReal = $null
try {
    Invoke-RestMethod "$base/auth/restablecer-contrasena" -Method Post -ContentType "application/json" `
        -Body (@{ email = "sola@local"; codigo = "000000"; password = "una frase larga nueva" } | ConvertTo-Json)
} catch { $errorCuentaReal = CuerpoDelError $_ }
$errorSinCuenta = $null
try {
    Invoke-RestMethod "$base/auth/restablecer-contrasena" -Method Post -ContentType "application/json" `
        -Body (@{ email = "nadie-nunca@local"; codigo = "000000"; password = "una frase larga nueva" } | ConvertTo-Json)
} catch { $errorSinCuenta = CuerpoDelError $_ }
Chequear ($errorCuentaReal -match "no es v") "un codigo equivocado se rechaza -> $errorCuentaReal"
Chequear ($errorCuentaReal -eq $errorSinCuenta) `
    "codigo equivocado y cuenta inexistente dan el mismo mensaje"

# La contrasena de Sola no cambio por intentar con un codigo equivocado.
$sigueEntrando = Entrar "sola@local" $PASSWORD
Chequear ($sigueEntrando.token.Length -gt 50) "un codigo equivocado no cambia la contrasena"

# ---------------------------------------------------------------------------
Titulo "14. Sumarse a un grupo, y salir"

# Tres cuentas descartables, cada una en su propio grupo de uno (asi arranca
# cualquier registro desde la v1.0). A diferencia de "12. Borrar la cuenta",
# aca la API ya sabe juntar gente sola: no hace falta JuntarEnGrupo/mongosh.
$sesionInvita  = RegistrarOEntrar "Invita"  "invita@local"  $PASSWORD
$sesionSuma    = RegistrarOEntrar "Suma"    "suma@local"    $PASSWORD
$sesionTercero = RegistrarOEntrar "Tercero" "tercero@local" $PASSWORD
$invita  = @{ Authorization = "Bearer $($sesionInvita.token)" }
$suma    = @{ Authorization = "Bearer $($sesionSuma.token)" }
$tercero = @{ Authorization = "Bearer $($sesionTercero.token)" }

# Un PERSONAL de Suma, ANTES de sumarse: tiene que seguir siendo suyo (y solo
# suyo) despues de mudarse de grupo.
$personalDeSumaAntes = Crear $suma @{ monto = 1500; categoriaId = $CAFE; fecha = "2026-09-06"
    descripcion = "antes de sumarme"; tipo = "PERSONAL" }

EsperarRegla { Invoke-RestMethod -Uri "$base/grupo/sumarse" -Method Post -Headers $suma `
    -ContentType "application/json" -Body (@{ codigo = "NOEXISTE1" } | ConvertTo-Json) } `
    "no es v" "un codigo inventado no suma a nadie"

$invitacion = Invoke-RestMethod -Uri "$base/grupo/invitar" -Method Post -Headers $invita
Chequear ($invitacion.codigo.Length -eq 8) "invitar genera un codigo de 8 caracteres"
Chequear (([datetime]$invitacion.vence) -gt (Get-Date).ToUniversalTime()) "y una fecha de vencimiento futura"

$grupoJunto = Invoke-RestMethod -Uri "$base/grupo/sumarse" -Method Post -Headers $suma `
    -ContentType "application/json" -Body (@{ codigo = $invitacion.codigo } | ConvertTo-Json)
Chequear (($grupoJunto.integrantes | Measure-Object).Count -eq 2) "sumarse con el codigo real junta a los dos"
Chequear (($grupoJunto.integrantes.nombre -contains "Invita") -and ($grupoJunto.integrantes.nombre -contains "Suma")) `
    "y el grupo tiene a Invita y a Suma"

EsperarRegla { Invoke-RestMethod -Uri "$base/grupo/invitar" -Method Post -Headers $invita } `
    "ya tiene a las dos personas" "invitar de nuevo se rechaza: el grupo ya esta completo"

$codigoDeTercero = (Invoke-RestMethod -Uri "$base/grupo/invitar" -Method Post -Headers $tercero).codigo
EsperarRegla { Invoke-RestMethod -Uri "$base/grupo/sumarse" -Method Post -Headers $suma `
    -ContentType "application/json" -Body (@{ codigo = $codigoDeTercero } | ConvertTo-Json) } `
    "en un grupo compartido" "Suma no puede sumarse a un segundo grupo estando ya de a dos"

Chequear ((Invoke-RestMethod -Uri "$base/gastos/$($personalDeSumaAntes.id)" -Headers $suma).id -eq $personalDeSumaAntes.id) `
    "el PERSONAL de Suma se mudo con ella: lo sigue viendo en el grupo nuevo"
EsperarCodigo { Invoke-RestMethod -Uri "$base/gastos/$($personalDeSumaAntes.id)" -Headers $invita } `
    404 "pero Invita NO lo ve: mudarse de grupo no vuelve publico un PERSONAL"

# Un COMPARTIDO que paga Suma mientras estan juntas, para probar que salir NO
# anonimiza: a diferencia de borrar-cuenta, la cuenta de Suma sigue existiendo.
$compartidoDeSuma = Crear $suma @{ monto = 3000; categoriaId = $CAFE; fecha = "2026-09-06"
    descripcion = "un cafe de las dos"; tipo = "COMPARTIDO"; porcentajePagador = 100 }

EsperarRegla { Invoke-RestMethod -Uri "$base/grupo/salir" -Method Post -Headers $suma `
    -ContentType "application/json" -Body (@{ password = "no-es-mi-contrasena" } | ConvertTo-Json) } `
    "no es correcta" "salir con la contrasena equivocada no mueve nada, y da 400 y no 401"

$grupoSola = Invoke-RestMethod -Uri "$base/grupo/salir" -Method Post -Headers $suma `
    -ContentType "application/json" -Body (@{ password = $PASSWORD } | ConvertTo-Json)
Chequear ((($grupoSola.integrantes | Measure-Object).Count -eq 1) -and ($grupoSola.integrantes[0].nombre -eq "Suma")) `
    "salir deja a Suma sola en un grupo nuevo"

$grupoDeInvitaSolo = Invoke-RestMethod -Uri "$base/grupo" -Headers $invita
Chequear ((($grupoDeInvitaSolo.integrantes | Measure-Object).Count -eq 1) -and ($grupoDeInvitaSolo.integrantes[0].nombre -eq "Invita")) `
    "e Invita queda sola en el grupo viejo"

$compartidoVistoPorInvita = Invoke-RestMethod -Uri "$base/gastos/$($compartidoDeSuma.id)" -Headers $invita
Chequear ($compartidoVistoPorInvita.pagadoPor.nombre -eq "Suma") `
    "el compartido que pago Suma le queda a Invita, con su nombre REAL (no 'Cuenta eliminada': la cuenta sigue existiendo)"
EsperarCodigo { Invoke-RestMethod -Uri "$base/gastos/$($compartidoDeSuma.id)" -Headers $suma } `
    404 "y Suma ya no lo ve: quedo en el grupo que dejo atras"

EsperarRegla { Invoke-RestMethod -Uri "$base/grupo/salir" -Method Post -Headers $suma `
    -ContentType "application/json" -Body (@{ password = $PASSWORD } | ConvertTo-Json) } `
    "no hay nadie" "estando sola, salir se rechaza: no hay pareja de la cual salir"

# Limpieza: borrar las tres cuentas descartables deja todo (gastos, grupos)
# atras, igual que en "12. Borrar la cuenta". Asi la proxima corrida arranca
# de cero sin tocar nada a mano.
foreach ($h in @($invita, $suma, $tercero)) {
    Invoke-WebRequest -Uri "$base/auth/borrar-cuenta" -Method Post -Headers $h -UseBasicParsing `
        -ContentType "application/json" -Body (@{ password = $PASSWORD } | ConvertTo-Json) | Out-Null
}

# ---------------------------------------------------------------------------
Titulo "15. Saldar deudas: liquidaciones"

# Pareja descartable propia, igual que en "12. Borrar la cuenta": asi el
# monto exacto del saldo total no depende de nada que hicieron las secciones
# anteriores. JuntarEnGrupo y no /grupo/sumarse: juntarlos no es lo que se
# prueba aca, eso ya lo cubre la seccion 14.
$sesionDeudor   = RegistrarOEntrar "Deudor"   "deudor@local"   $PASSWORD
$sesionAcreedor = RegistrarOEntrar "Acreedor" "acreedor@local" $PASSWORD
JuntarEnGrupo "deudor@local" "acreedor@local"
$deudor   = @{ Authorization = "Bearer $($sesionDeudor.token)" }
$acreedor = @{ Authorization = "Bearer $($sesionAcreedor.token)" }

# Acreedor paga $1000 al 50/50: Deudor le queda debiendo 500.
Crear $acreedor @{ monto = 1000; categoriaId = $CAFE; fecha = "2026-09-06"
    descripcion = "compartido para el saldo total"; tipo = "COMPARTIDO"; porcentajePagador = 50 } | Out-Null

$totalDeudor = Invoke-RestMethod -Uri "$base/saldo/total" -Headers $deudor
Chequear ($totalDeudor.monto -eq 500 -and $totalDeudor.aFavorMio -eq -500) `
    "el saldo total (sin liquidaciones) es la deuda del compartido: Deudor debe 500"

$totalAcreedor = Invoke-RestMethod -Uri "$base/saldo/total" -Headers $acreedor
Chequear ($totalAcreedor.aFavorMio -eq 500) "y del otro lado, a Acreedor le deben 500"

EsperarValidacion { Invoke-RestMethod -Uri "$base/saldo/liquidaciones" -Method Post -Headers $deudor `
    -ContentType "application/json" -Body (@{ monto = 0; meLoPagaron = $false } | ConvertTo-Json) } `
    "monto" "una liquidacion de cero se rechaza por Bean Validation, no por regla de negocio"

EsperarRegla { Invoke-RestMethod -Uri "$base/saldo/liquidaciones" -Method Post -Headers $sola `
    -ContentType "application/json" -Body (@{ monto = 100; meLoPagaron = $false } | ConvertTo-Json) } `
    "otra persona tiene que estar" "estando sola, no hay a quien pagarle"

# Pago parcial: Deudor paga 200 de los 500. meLoPagaron = false: "yo pague".
$despuesDelPrimerPago = Invoke-RestMethod -Uri "$base/saldo/liquidaciones" -Method Post -Headers $deudor `
    -ContentType "application/json" -Body (@{ monto = 200; meLoPagaron = $false } | ConvertTo-Json)
Chequear ($despuesDelPrimerPago.monto -eq 300 -and $despuesDelPrimerPago.deudorId -eq $sesionDeudor.usuario.id) `
    "el pago parcial baja la deuda de 500 a 300, y Deudor sigue debiendo"

# Pago del resto, pero anotado por QUIEN LO RECIBIO: Acreedor llama con
# meLoPagaron = true ("Deudor me pago 300"). Mismo resultado que si hubiera
# llamado Deudor con meLoPagaron = false -- es la misma liquidacion, contada
# desde el otro lado.
$despuesDelSegundoPago = Invoke-RestMethod -Uri "$base/saldo/liquidaciones" -Method Post -Headers $acreedor `
    -ContentType "application/json" -Body (@{ monto = 300; meLoPagaron = $true } | ConvertTo-Json)
Chequear ($despuesDelSegundoPago.monto -eq 0 -and $null -eq $despuesDelSegundoPago.deudorId) `
    "el segundo pago liquida el resto: quedan a mano"

$historial = Invoke-RestMethod -Uri "$base/saldo/liquidaciones" -Headers $acreedor
Chequear ((($historial | Measure-Object).Count -eq 2) -and ($historial[0].monto -eq 300)) `
    "el historial tiene los dos pagos, el mas nuevo primero"
Chequear ($historial[0].de.nombre -eq "Deudor" -and $historial[0].para.nombre -eq "Acreedor") `
    "y el que anoto Acreedor con meLoPagaron queda IGUAL guardado: de Deudor, para Acreedor"

# El saldo del MES no cambia por nada de esto: sigue siendo el mismo
# endpoint de siempre, sin liquidaciones.
$saldoDelMes = Invoke-RestMethod -Uri "$base/saldo?mes=2026-09" -Headers $deudor
Chequear ($saldoDelMes.monto -eq 500) "GET /saldo del mes no se toco: sigue viendo la deuda sin descontar los pagos"

foreach ($h in @($deudor, $acreedor)) {
    Invoke-WebRequest -Uri "$base/auth/borrar-cuenta" -Method Post -Headers $h -UseBasicParsing `
        -ContentType "application/json" -Body (@{ password = $PASSWORD } | ConvertTo-Json) | Out-Null
}

# ---------------------------------------------------------------------------
Titulo "16. Mi Plata: el balance personal"

$sesionAhorrista = RegistrarOEntrar "Ahorrista" "ahorrista@local" $PASSWORD
$ahorrista = @{ Authorization = "Bearer $($sesionAhorrista.token)" }

$vacio = Invoke-RestMethod -Uri "$base/balance-personal" -Headers $ahorrista
Chequear ($vacio.ingresado -eq 0 -and $vacio.gastado -eq 0 -and $vacio.restante -eq 0) `
    "sin nada cargado, Mi Plata arranca en cero"

EsperarValidacion { Invoke-RestMethod -Uri "$base/balance-personal/ingresos" -Method Post -Headers $ahorrista `
    -ContentType "application/json" -Body (@{ monto = 0 } | ConvertTo-Json) } `
    "monto" "un ingreso de cero se rechaza por Bean Validation (seccion 2.3c: ya no por regla de negocio)"

# A diferencia de un Aporte, un Ingreso ya NO admite negativo: ver mas abajo
# donde se edita y se borra de verdad, que es lo que reemplazo al asiento en
# contrario (seccion 2.3c, a pedido de Franco probando la app en el telefono).
EsperarValidacion { Invoke-RestMethod -Uri "$base/balance-personal/ingresos" -Method Post -Headers $ahorrista `
    -ContentType "application/json" -Body (@{ monto = -10000 } | ConvertTo-Json) } `
    "monto" "un ingreso negativo tambien se rechaza: ya no es la forma de corregir uno mal cargado"

$conIngreso = Invoke-RestMethod -Uri "$base/balance-personal/ingresos" -Method Post -Headers $ahorrista `
    -ContentType "application/json" -Body (@{ monto = 80000 } | ConvertTo-Json)
Chequear ($conIngreso.ingresado -eq 80000 -and $conIngreso.restante -eq 80000) `
    "el primer ingreso queda reflejado, y el restante es igual porque todavia no gasto nada"

$idIngreso = $conIngreso.ingresos[0].id
Chequear ($null -ne $idIngreso) `
    "el ingreso que vuelve trae id (seccion 2.3c): hace falta para poder editarlo o borrarlo"

# Un gasto PERSONAL descuenta solo del restante, sin ningun paso extra.
$gastoPersonal = Crear $ahorrista @{
    monto = 25000; categoriaId = $CAFE; fecha = "2026-09-06"
    descripcion = "compras de la semana"; tipo = "PERSONAL"
}
$despuesDelGasto = Invoke-RestMethod -Uri "$base/balance-personal" -Headers $ahorrista
Chequear ($despuesDelGasto.gastado -eq 25000 -and $despuesDelGasto.restante -eq 55000) `
    "un gasto personal baja el restante solo, sin tocar el ingreso"

# Editar y borrar (seccion 2.3c): a diferencia de un aporte a la vaquita o una
# liquidacion -- que siguen siendo ledgers inmutables -- un ingreso mal
# cargado ahora se corrige o se saca de verdad, tocando la fila.
EsperarValidacion { Invoke-RestMethod -Uri "$base/balance-personal/ingresos/$idIngreso" -Method Put -Headers $ahorrista `
    -ContentType "application/json" -Body (@{ monto = 0 } | ConvertTo-Json) } `
    "monto" "editar un ingreso a cero se rechaza, mismo @Positive que crearlo"

$editado = Invoke-RestMethod -Uri "$base/balance-personal/ingresos/$idIngreso" -Method Put -Headers $ahorrista `
    -ContentType "application/json" -Body (@{ monto = 60000 } | ConvertTo-Json)
Chequear ($editado.ingresado -eq 60000 -and $editado.restante -eq 35000) `
    "editar un ingreso corrige el monto de verdad, sin dejar un segundo asiento"

EsperarCodigo { Invoke-RestMethod -Uri "$base/balance-personal/ingresos/id-que-no-existe" -Method Put -Headers $ahorrista `
    -ContentType "application/json" -Body (@{ monto = 1000 } | ConvertTo-Json) } 404 `
    "editar un id de ingreso inexistente da 404"

$borrado = Invoke-RestMethod -Uri "$base/balance-personal/ingresos/$idIngreso" -Method Delete -Headers $ahorrista
Chequear ($borrado.ingresado -eq 0 -and $borrado.restante -eq -25000 -and (($borrado.ingresos | Measure-Object).Count -eq 0)) `
    "borrar el unico ingreso lo saca del todo: ingresado vuelve a cero y el historial queda vacio"

EsperarCodigo { Invoke-RestMethod -Uri "$base/balance-personal/ingresos/$idIngreso" -Method Delete -Headers $ahorrista } 404 `
    "borrar el mismo id de nuevo da 404: ya no existe"

Invoke-RestMethod -Uri "$base/gastos/$($gastoPersonal.id)" -Method Delete -Headers $ahorrista | Out-Null
Invoke-WebRequest -Uri "$base/auth/borrar-cuenta" -Method Post -Headers $ahorrista -UseBasicParsing `
    -ContentType "application/json" -Body (@{ password = $PASSWORD } | ConvertTo-Json) | Out-Null

# ---------------------------------------------------------------------------
Write-Host ""
if ($fallos -eq 0) {
    Write-Host "TODO OK" -ForegroundColor Green
} else {
    Write-Host "$fallos CHEQUEOS FALLARON" -ForegroundColor Red
}
