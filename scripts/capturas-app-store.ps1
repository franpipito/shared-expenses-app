# Lleva las capturas del telefono al tamano que pide App Store Connect.
#
# El slot obligatorio de capturas es el de iPhone 6,9" (1290 x 2796, entre otros
# tamanos aceptados), y un iPhone 13 Pro saca 1170 x 2532. La proporcion es casi
# la misma: escalando a 2796 de alto quedan 1292 de ancho, y se recorta un pixel
# de cada lado. Nada se deforma.
#
# Ademas App Store Connect rechaza imagenes con canal alfa, asi que se guardan
# en RGB de 24 bits.
#
# Uso (desde la raiz del repo):
#   1. Copia las capturas del telefono a la carpeta capturas\
#   2. .\scripts\capturas-app-store.ps1
#   3. Subi lo que queda en capturas\app-store\
#
# La carpeta capturas\ esta en .gitignore: no se versiona.

param(
    [string]$Entrada = "capturas",
    [string]$Salida  = "capturas\app-store",
    [int]$Ancho = 1290,
    [int]$Alto  = 2796
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

if (-not (Test-Path $Entrada)) {
    Write-Host "No existe la carpeta '$Entrada'. Crea la carpeta y copia ahi las capturas." -ForegroundColor Red
    exit 1
}
New-Item -ItemType Directory -Force $Salida | Out-Null

$archivos = Get-ChildItem $Entrada -File | Where-Object { $_.Extension -match '^\.(png|jpe?g)$' }
if (-not $archivos) {
    Write-Host "No hay capturas (.png o .jpg) en '$Entrada'." -ForegroundColor Yellow
    exit 1
}

foreach ($f in $archivos) {
    $origen = [System.Drawing.Image]::FromFile($f.FullName)
    try {
        if ($origen.Width -gt $origen.Height) {
            Write-Host "  SALTEADA  $($f.Name): esta apaisada, y la app es solo vertical." -ForegroundColor Yellow
            continue
        }

        # "Cubrir": escalar hasta llenar el destino en las dos dimensiones y
        # recortar lo que sobre, centrado. Con la proporcion del 13 Pro sobra un
        # pixel por lado; si sobrara mucho, la captura no es de un iPhone de los
        # que se esperan y conviene mirarla antes de subirla.
        $escala = [Math]::Max($Ancho / $origen.Width, $Alto / $origen.Height)
        $w = [int][Math]::Round($origen.Width * $escala)
        $h = [int][Math]::Round($origen.Height * $escala)
        $recorteX = $w - $Ancho
        $recorteY = $h - $Alto
        if ($recorteX -gt $Ancho * 0.02 -or $recorteY -gt $Alto * 0.02) {
            Write-Host "  OJO       $($f.Name): $($origen.Width)x$($origen.Height) tiene otra proporcion; se recortan $recorteX x $recorteY px. Mirala antes de subirla." -ForegroundColor Yellow
        }

        $destino = New-Object System.Drawing.Bitmap $Ancho, $Alto, ([System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
        $g = [System.Drawing.Graphics]::FromImage($destino)
        $g.InterpolationMode  = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $g.PixelOffsetMode    = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
        $g.SmoothingMode      = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
        # TileFlipXY: sin esto, el bicubico mezcla el borde con negro y queda un
        # filo oscuro de un pixel alrededor de la imagen.
        $atributos = New-Object System.Drawing.Imaging.ImageAttributes
        $atributos.SetWrapMode([System.Drawing.Drawing2D.WrapMode]::TileFlipXY)
        $rect = New-Object System.Drawing.Rectangle ([int](-$recorteX / 2)), ([int](-$recorteY / 2)), $w, $h
        $g.DrawImage($origen, $rect, 0, 0, $origen.Width, $origen.Height, [System.Drawing.GraphicsUnit]::Pixel, $atributos)
        $g.Dispose()

        $nombre = [System.IO.Path]::GetFileNameWithoutExtension($f.Name) + ".png"
        $destino.Save((Join-Path (Resolve-Path $Salida) $nombre), [System.Drawing.Imaging.ImageFormat]::Png)
        $destino.Dispose()
        Write-Host "  OK        $($f.Name)  $($origen.Width)x$($origen.Height) -> ${Ancho}x${Alto}" -ForegroundColor Green
    } finally {
        $origen.Dispose()
    }
}

Write-Host ""
Write-Host "Listas en '$Salida'. Van en App Store Connect -> la version -> iPhone 6,9`"." -ForegroundColor Cyan
