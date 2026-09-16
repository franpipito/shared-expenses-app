# Smoke test de la API de gastos.
#
# Requiere:
#   - la app corriendo en localhost:8080
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
$CODIGO   = "nutrias"          # app.registro.codigo-invitacion
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

function Registrar($nombre, $email, $password, $codigo) {
    $body = @{ nombre = $nombre; email = $email; password = $password
               codigoInvitacion = $codigo } | ConvertTo-Json
    return Invoke-RestMethod -Uri "$base/auth/registro" -Method Post `
        -ContentType "application/json" -Body $body
}

function Entrar($email, $password) {
    $body = @{ email = $email; password = $password } | ConvertTo-Json
    return Invoke-RestMethod -Uri "$base/auth/login" -Method Post `
        -ContentType "application/json" -Body $body
}

# Registra si no existe, y si ya existe entra. Asi el script se puede correr
# muchas veces seguidas sin tener que limpiar usuarios entre corridas.
#
# El catch mira POR QUE fallo el registro. Un catch a secas que cayera siempre al
# login esconde el error real: si el registro falla por otra cosa, lo que ves es
# un "email o contrasena incorrectos" que no tiene nada que ver con la causa.
function RegistrarOEntrar($nombre, $email, $password) {
    try {
        return Registrar $nombre $email $password $CODIGO
    } catch {
        $cuerpo = CuerpoDelError $_
        if ($cuerpo -match "ya esta registrado") {
            return Entrar $email $password
        }
        throw "No se pudo registrar a $nombre, y NO es porque ya exista. El backend dijo: $cuerpo"
    }
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

EsperarRegla { Registrar "Intruso" "intruso@local" $PASSWORD "codigo-equivocado" } `
    "codigo de invitacion" "sin el codigo de invitacion no se puede registrar"

EsperarRegla { Registrar "Franco" "franco@local" $PASSWORD $CODIGO } `
    "ya esta registrado" "no se puede registrar dos veces el mismo email"

EsperarRegla { Registrar "Tercero" "tercero@local" $PASSWORD $CODIGO } `
    "grupo ya esta completo" "un tercer integrante se rechaza"

EsperarCodigo { Invoke-RestMethod -Uri "$base/gastos?mes=2026-09" `
    -Headers @{ Authorization = "Bearer esto.no.es-un-token" } } `
    401 "un token invalido da 401"

# El header viejo de la sesion 2 ya no autentica nada.
EsperarCodigo { Invoke-RestMethod -Uri "$base/gastos?mes=2026-09" `
    -Headers @{ "X-Usuario-Id" = "1" } } `
    401 "el header X-Usuario-Id ya no sirve"

# --- politica de contrasenas ---
EsperarRegla { Registrar "Corta" "corta@local" "Abc123!x" $CODIGO } `
    "al menos 12" "una contrasena de menos de 12 caracteres se rechaza"

EsperarRegla { Registrar "Comun" "comun@local" "123456789012" $CODIGO } `
    "demasiado comun" "una contrasena comun se rechaza aunque sea larga"

EsperarRegla { Registrar "Homonimo" "homonimo@local" "homonimo-del-sur" $CODIGO } `
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

EsperarValidacion { Crear $franco @{ monto = 100; categoriaId = $CAFE; fecha = "2026-09-06"
                                     descripcion = ""; tipo = "PERSONAL" } } `
    "descripcion" "descripcion vacia rechazada por @NotBlank"

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
    "No existe la categoria" "categoria inexistente rechazada por el servicio"

EsperarRegla { Crear $franco @{ monto = 100; categoriaId = $CAFE; fecha = "2026-09-06"
                                descripcion = "x"; tipo = "PERSONAL"; pagadoPorId = $ELLA_ID } } `
    "solo lo puede cargar quien lo pago" "no se puede cargar un personal a nombre de otro"

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
$pozo = Invoke-RestMethod -Uri "$base/pozos/$($pozo.id)/aportes" -Method Post -Headers $franco `
    -ContentType "application/json" -Body (@{ monto = 400000.00 } | ConvertTo-Json)
Chequear ($pozo.aportado -eq 400000.00) "el aporte de Franco entra"

$pozo = Invoke-RestMethod -Uri "$base/pozos/$($pozo.id)/aportes" -Method Post -Headers $ella `
    -ContentType "application/json" -Body (@{ monto = 400000.00 } | ConvertTo-Json)
Chequear ($pozo.aportado -eq 800000.00) "los dos aportes suman"
Chequear ($pozo.restante -eq 800000.00) "sin gastos, el restante es todo lo aportado"

# El aporte se registra SIEMPRE a nombre de quien hace la request: no hay forma
# de anotar plata a nombre de la otra persona.
$deElla = $pozo.porPersona | Where-Object { $_.usuarioId -eq $ELLA_ID }
Chequear ($deElla.total -eq 400000.00) "cada aporte queda a nombre de quien lo hizo"

# Un aporte equivocado se deshace compensandolo, no borrandolo: los aportes son
# inmutables a proposito. Antes esto era imposible (el monto era @Positive y no
# hay endpoint para borrar un aporte), asi que un 4.000.000 tipeado en vez de
# 400.000 quedaba en el pozo para siempre.
$conError = Invoke-RestMethod -Uri "$base/pozos/$($pozo.id)/aportes" -Method Post -Headers $franco `
    -ContentType "application/json" -Body (@{ monto = 90000.00 } | ConvertTo-Json)
Chequear ($conError.aportado -eq 890000.00) "un aporte de mas entra"

$corregido = Invoke-RestMethod -Uri "$base/pozos/$($pozo.id)/aportes" -Method Post -Headers $franco `
    -ContentType "application/json" -Body (@{ monto = -90000.00 } | ConvertTo-Json)
Chequear ($corregido.aportado -eq 800000.00) "y se deshace con un aporte negativo"
Chequear (($corregido.aportes | Measure-Object).Count -eq 4) `
    "quedan los dos asientos, no se borra ninguno"

# EsperarRegla y no EsperarValidacion: el cero lo rechaza PozoServicio, no una
# anotacion. Es donde va -- Bean Validation no tiene un @NotZero, y escribir una
# anotacion propia para un solo campo es mas maquinaria que regla.
EsperarRegla { Invoke-RestMethod -Uri "$base/pozos/$($pozo.id)/aportes" -Method Post -Headers $franco `
    -ContentType "application/json" -Body (@{ monto = 0 } | ConvertTo-Json) } `
    "no puede ser cero" "un aporte de cero se rechaza: no es aporte ni correccion"

$pozo = $corregido

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
    } } "ya esta cerrada" "no se puede cargar un gasto a una vaquita cerrada"

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
Write-Host ""
if ($fallos -eq 0) {
    Write-Host "TODO OK" -ForegroundColor Green
} else {
    Write-Host "$fallos CHEQUEOS FALLARON" -ForegroundColor Red
}
