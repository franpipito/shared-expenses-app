# Crea la cuenta demo para App Review y le carga gastos de ejemplo.
#
# Para que sirve: App Store Connect pide una cuenta con la que el revisor pueda
# entrar ("Sign-In Information"). Y la misma cuenta sirve para sacar las
# capturas de la tienda, asi no aparecen los gastos reales de nadie.
#
# Uso (desde la raiz del repo, en PowerShell):
#   .\scripts\crear-cuenta-demo.ps1
#
# Pide la contrasena por teclado, sin mostrarla y sin guardarla en ningun lado.
# Guardala en tu gestor de contrasenas: va en App Store Connect.
#
# CORRELO EL DIA QUE MANDES A REVISION. Los gastos se fechan relativo a HOY (mes
# en curso y el anterior). Si la revision cae en un mes nuevo, el resumen de ese
# mes arranca vacio -- los datos siguen en el mes anterior, con el selector de
# mes -- y alcanza con volver a correrlo.
#
# Se puede correr las veces que quieras: si la cuenta existe, entra en vez de
# registrarla, y cada gasto lleva una clave de idempotencia fija (clienteId), la
# misma que protege la cola offline. Correrlo dos veces no duplica nada.
#
# Todos los gastos son PERSONAL: una cuenta nueva esta sola en su grupo, y
# sumarse al de otra persona llega en la v1.1.

param(
    [string]$Base   = "https://minutria-api.onrender.com",
    [string]$Email  = "revision@minutria.app",
    [string]$Nombre = "Viole"
)

$ErrorActionPreference = "Stop"

function CuerpoDelError($err) {
    try {
        $stream = $err.Exception.Response.GetResponseStream()
        $stream.Position = 0
        return (New-Object System.IO.StreamReader($stream)).ReadToEnd()
    } catch {
        return ""
    }
}

function Pedir($metodo, $ruta, $cuerpo, $headers) {
    $parametros = @{ Uri = "$Base$ruta"; Method = $metodo; TimeoutSec = 120 }
    if ($headers) { $parametros.Headers = $headers }
    if ($cuerpo) {
        $parametros.ContentType = "application/json; charset=utf-8"
        # En UTF-8 explicito: PowerShell 5.1 manda el cuerpo en la codificacion
        # del sistema, y las tildes de las descripciones llegarian rotas.
        $parametros.Body = [System.Text.Encoding]::UTF8.GetBytes(($cuerpo | ConvertTo-Json))
    }
    return Invoke-RestMethod @parametros
}

# --- 1. Que el backend conteste. En Render puede estar despertando (40-60s). ---
Write-Host "Esperando al backend en $Base ..." -ForegroundColor Cyan
try {
    $salud = Invoke-RestMethod -Uri "$Base/actuator/health" -TimeoutSec 120
    if ($salud.status -ne "UP") { throw "status = $($salud.status)" }
} catch {
    Write-Host "El backend no contesta: $_" -ForegroundColor Red
    exit 1
}

# --- 2. La contrasena. ---
# La variable de entorno es para probar el script sin teclado (contra localhost).
$password = $env:MINUTRIA_DEMO_PASSWORD
if (-not $password) {
    Write-Host ""
    Write-Host "Elegi la contrasena de la cuenta demo: 12 caracteres o mas, y que no" -ForegroundColor Yellow
    Write-Host "contenga '$Nombre' ni el email. Una frase sirve." -ForegroundColor Yellow
    $a = Read-Host "Contrasena" -AsSecureString
    $b = Read-Host "Repetila" -AsSecureString
    $plano = { param($s) [Runtime.InteropServices.Marshal]::PtrToStringAuto(
                   [Runtime.InteropServices.Marshal]::SecureStringToBSTR($s)) }
    $password = & $plano $a
    if ($password -ne (& $plano $b)) {
        Write-Host "No coinciden." -ForegroundColor Red
        exit 1
    }
}

# --- 3. Registrar, o entrar si ya existe. ---
try {
    $sesion = Pedir Post "/auth/registro" @{ nombre = $Nombre; email = $Email; password = $password }
    Write-Host "Cuenta creada: $Email" -ForegroundColor Green
} catch {
    $cuerpo = CuerpoDelError $_
    if ($cuerpo -match "registrado") {
        $sesion = Pedir Post "/auth/login" @{ email = $Email; password = $password }
        Write-Host "La cuenta ya existia: entre con ella." -ForegroundColor Green
    } else {
        Write-Host "No se pudo registrar. El backend dijo: $cuerpo" -ForegroundColor Red
        exit 1
    }
}
$headers = @{ Authorization = "Bearer $($sesion.token)" }

# --- 4. Las categorias, por nombre: los ids son ObjectId distintos en cada base. ---
$categorias = @{}
foreach ($c in (Pedir Get "/categorias" $null $headers)) { $categorias[$c.nombre] = $c.id }

# --- 5. Los gastos. ---
# Pensados para las capturas: los dos uber de $4.000 (uno hormiga y el otro no,
# que es literalmente el producto), uno sin descripcion (es opcional), y el mes
# anterior con mas gasto evitable, para que la nutria tenga contra que
# compararse y salga contenta.
$hoy      = Get-Date
$esteMes  = Get-Date -Year $hoy.Year -Month $hoy.Month -Day 1
$mesPasado = $esteMes.AddMonths(-1)

#            dia  categoria  monto   hormiga  descripcion
$delMes = @(
    @(  1, "comida",  18500, $false, "super de la semana"),
    @(  2, "cafe",     3200, $true,  "café con medialunas"),
    @(  3, "uber",     4000, $false, "uber a lo de mi vieja"),
    @(  4, "uber",     4000, $true,  "uber de vuelta, por fiaca"),
    @(  6, "ropa",    32000, $false, "remera para el cumple"),
    @(  8, "cafe",     2800, $true,  ""),
    @( 10, "regalos", 15000, $false, "regalo para mamá"),
    @( 12, "comida",   9800, $true,  "delivery del viernes"),
    @( 15, "otros",    6500, $false, "farmacia"),
    @( 18, "cafe",     3100, $true,  "café de la oficina"),
    @( 21, "comida",  21000, $false, "super"),
    @( 24, "uber",     5200, $false, "uber por la lluvia"),
    @( 26, "cafe",     3500, $true,  "")
)
$delMesPasado = @(
    @(  2, "cafe",     3400, $true,  "café"),
    @(  3, "comida",  12000, $true,  "delivery"),
    @(  5, "uber",     4500, $true,  "uber, llegaba tarde"),
    @(  7, "comida",  20000, $false, "super"),
    @(  9, "cafe",     3000, $true,  "café con alfajor"),
    @( 11, "ropa",    25000, $true,  "zapatillas en oferta"),
    @( 14, "comida",  11000, $true,  "delivery"),
    @( 17, "uber",     6000, $true,  "uber a la salida"),
    @( 20, "cafe",     3300, $true,  "café"),
    @( 23, "comida",  19000, $false, "super"),
    @( 27, "otros",    7000, $false, "peluquería")
)

function CargarMes($inicioDelMes, $plantillas) {
    $diasDelMes = [DateTime]::DaysInMonth($inicioDelMes.Year, $inicioDelMes.Month)
    $cargados = 0
    for ($i = 0; $i -lt $plantillas.Count; $i++) {
        $p = $plantillas[$i]
        $dia = [Math]::Min($p[0], $diasDelMes)
        $fecha = $inicioDelMes.AddDays($dia - 1)
        # El backend rechaza fechas futuras: del mes en curso, solo hasta hoy.
        if ($fecha.Date -gt $hoy.Date) { continue }

        $gasto = @{
            monto       = $p[2]
            categoriaId = $categorias[$p[1]]
            fecha       = $fecha.ToString("yyyy-MM-dd")
            tipo        = "PERSONAL"
            esHormiga   = $p[3]
            # Fija por mes y posicion: correr el script de nuevo devuelve el
            # gasto que ya estaba en vez de crear otro.
            clienteId   = "demo-" + $inicioDelMes.ToString("yyyy-MM") + "-" + $i
        }
        if ($p[4]) { $gasto.descripcion = $p[4] }

        Pedir Post "/gastos" $gasto $headers | Out-Null
        $cargados++
    }
    return $cargados
}

$n1 = CargarMes $mesPasado $delMesPasado
$n2 = CargarMes $esteMes $delMes
Write-Host "Gastos: $n1 del mes pasado y $n2 de este mes (los que ya estaban no se duplican)." -ForegroundColor Green

# --- 6. Como lo va a ver el revisor. ---
$resumen = Pedir Get "/gastos/resumen" $null $headers
Write-Host ""
Write-Host "Resumen de este mes, como lo ve la app:" -ForegroundColor Cyan
Write-Host "  total del mes:  $($resumen.total)"
Write-Host "  gasto hormiga:  $($resumen.totalHormiga)"
Write-Host "  nutria:         $($resumen.animo)"
Write-Host "  tiene pareja:   $($resumen.tienePareja)  (tiene que ser False)"
Write-Host ""
Write-Host "Para App Store Connect -> App Review Information -> Sign-In Information:" -ForegroundColor Yellow
Write-Host "  usuario:     $Email"
Write-Host "  contrasena:  la que acabas de elegir"
