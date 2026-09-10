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

# ---------------------------------------------------------------------------
Titulo "9. Limpieza"

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
