# Smoke test de la API de gastos.
#
# Requiere:
#   - la app corriendo en localhost:8080
#   - el seed cargado (scripts/seed-desarrollo.sql), con Franco=1 y Ella=2
#
# Correr desde la raiz del repo:
#   .\scripts\smoke-test.ps1
#
# Crea gastos de prueba y los BORRA al final.

$ErrorActionPreference = "Stop"

$base   = "http://localhost:8080"
$franco = @{ "X-Usuario-Id" = "1" }
$ella   = @{ "X-Usuario-Id" = "2" }

# ids de categoria del seed
$CAFE = 1; $COMIDA = 3

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

$soloFranco = Invoke-RestMethod -Uri "$base/gastos?mes=2026-09&pagadoPor=1" -Headers $ella
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

EsperarRegla { Crear $franco @{ monto = 100; categoriaId = 999; fecha = "2026-09-06"
                                descripcion = "x"; tipo = "PERSONAL" } } `
    "No existe la categoria" "categoria inexistente rechazada por el servicio"

EsperarRegla { Crear $franco @{ monto = 100; categoriaId = $CAFE; fecha = "2026-09-06"
                                descripcion = "x"; tipo = "PERSONAL"; pagadoPorId = 2 } } `
    "solo lo puede cargar quien lo pago" "no se puede cargar un personal a nombre de otro"

EsperarCodigo { Invoke-RestMethod -Uri "$base/gastos?mes=2026-09" } `
    401 "sin header X-Usuario-Id da 401"

# Sin esHormiga en el cuerpo: tiene que crearse igual, con esHormiga=false.
$sinFlag = Crear $franco @{ monto = 100; categoriaId = $CAFE; fecha = "2026-09-06"
                            descripcion = "sin el flag"; tipo = "PERSONAL" }
Chequear ($sinFlag.esHormiga -eq $false) "omitir esHormiga no rompe el parseo y default a false"
Invoke-RestMethod -Uri "$base/gastos/$($sinFlag.id)" -Method Delete -Headers $franco | Out-Null

# ---------------------------------------------------------------------------
Titulo "7. Edicion y bloqueo optimista"

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
Titulo "8. Limpieza"

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
