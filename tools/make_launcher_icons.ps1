# Rebuilds Android launcher bitmaps from the master art file.
# Usage: powershell -ExecutionPolicy Bypass -File tools/make_launcher_icons.ps1
Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$source = Join-Path $root 'assets/icon_new.png'
$resDir = Join-Path $root 'app/src/main/res'

if (-not (Test-Path $source)) { throw "Master icon not found: $source" }

$densities = @(
    @{ name = 'mdpi';    legacy = 48;  adaptive = 108 },
    @{ name = 'hdpi';    legacy = 72;  adaptive = 162 },
    @{ name = 'xhdpi';   legacy = 96;  adaptive = 216 },
    @{ name = 'xxhdpi';  legacy = 144; adaptive = 324 },
    @{ name = 'xxxhdpi'; legacy = 192; adaptive = 432 }
)

$master = [System.Drawing.Image]::FromFile($source)

function New-Canvas([int]$size) {
    $bmp = New-Object System.Drawing.Bitmap($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.Clear([System.Drawing.Color]::Transparent)
    return @($bmp, $g)
}

foreach ($d in $densities) {
    $dir = Join-Path $resDir ("mipmap-" + $d.name)
    New-Item -ItemType Directory -Force -Path $dir | Out-Null

    # Legacy icon: rounded square so the silhouette is not a filled rectangle.
    $pair = New-Canvas $d.legacy
    $bmp = $pair[0]; $g = $pair[1]
    $size = [int]$d.legacy
    $radius = [int][Math]::Round($size * 0.22)
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $path.AddArc(0, 0, $radius * 2, $radius * 2, 180, 90)
    $path.AddArc($size - $radius * 2, 0, $radius * 2, $radius * 2, 270, 90)
    $path.AddArc($size - $radius * 2, $size - $radius * 2, $radius * 2, $radius * 2, 0, 90)
    $path.AddArc(0, $size - $radius * 2, $radius * 2, $radius * 2, 90, 90)
    $path.CloseFigure()
    $g.SetClip($path)
    $g.DrawImage($master, 0, 0, $size, $size)
    $g.Dispose()
    $path.Dispose()
    $bmp.Save((Join-Path $dir 'ic_launcher.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()

    # Legacy round icon: circular clip.
    $pair = New-Canvas $d.legacy
    $bmp = $pair[0]; $g = $pair[1]
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $path.AddEllipse(0, 0, $d.legacy, $d.legacy)
    $g.SetClip($path)
    $g.DrawImage($master, 0, 0, $d.legacy, $d.legacy)
    $g.Dispose()
    $path.Dispose()
    $bmp.Save((Join-Path $dir 'ic_launcher_round.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()

    # Adaptive layers: full-bleed so the launcher mask is filled edge to edge.
    $size = [int]$d.adaptive
    $pair = New-Canvas $size
    $bmp = $pair[0]; $g = $pair[1]
    $g.DrawImage($master, 0, 0, $size, $size)
    $g.Dispose()
    $bmp.Save((Join-Path $dir 'ic_launcher_foreground.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Save((Join-Path $dir 'ic_launcher_background.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()

    Write-Output ("wrote " + $d.name)
}

$master.Dispose()
Write-Output 'launcher icons rebuilt'
