Add-Type -AssemblyName System.Drawing

$srcLogoPath = "c:\Users\jayde\OneDrive\Desktop\Brain\app\src\main\res\drawable\ic_app_logo.png"
$resDir = "c:\Users\jayde\OneDrive\Desktop\Brain\app\src\main\res"

$logo = [System.Drawing.Image]::FromFile($srcLogoPath)
Write-Host "Loaded logo: $($logo.Width) x $($logo.Height)"

# 1. Create pre-padded foreground PNG (no inset XML needed!)
function Create-PaddedForeground($sourceImg, $totalSize, $marginRatio, $outPath) {
    $bmp = New-Object System.Drawing.Bitmap($totalSize, $totalSize, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.Clear([System.Drawing.Color]::Transparent)

    $margin = [int]($totalSize * $marginRatio)
    $drawSize = $totalSize - ($margin * 2)
    $g.DrawImage($sourceImg, $margin, $margin, $drawSize, $drawSize)
    $g.Dispose()

    $bmp.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Host "Created foreground: $outPath ($totalSize x $totalSize)"
}

# 2. Create standalone composite launcher icon (white bg + logo)
function Create-CompositeLauncherIcon($sourceImg, $size, $outPath, $isRound) {
    $bmp = New-Object System.Drawing.Bitmap($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.Clear([System.Drawing.Color]::Transparent)

    $brush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)
    if ($isRound) {
        $g.FillEllipse($brush, 0, 0, $size, $size)
    } else {
        $radius = [int]($size * 0.22)
        $rect = New-Object System.Drawing.Rectangle(0, 0, $size, $size)
        $path = New-Object System.Drawing.Drawing2D.GraphicsPath
        $d = $radius * 2
        $path.AddArc($rect.X, $rect.Y, $d, $d, 180, 90)
        $path.AddArc($rect.Right - $d, $rect.Y, $d, $d, 270, 90)
        $path.AddArc($rect.Right - $d, $rect.Bottom - $d, $d, $d, 0, 90)
        $path.AddArc($rect.X, $rect.Bottom - $d, $d, $d, 90, 90)
        $path.CloseFigure()
        $g.FillPath($brush, $path)
        $path.Dispose()
    }
    $brush.Dispose()

    $margin = [int]($size * 0.12)
    $drawSize = $size - ($margin * 2)
    $g.DrawImage($sourceImg, $margin, $margin, $drawSize, $drawSize)
    $g.Dispose()

    $bmp.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Host "Created launcher icon: $outPath ($size x $size)"
}

# Generate foregrounds
$fgSizes = @{
    "drawable" = 432
    "drawable-xxxhdpi" = 432
    "drawable-xxhdpi" = 324
    "drawable-xhdpi" = 216
    "drawable-hdpi" = 162
    "drawable-mdpi" = 108
}

foreach ($e in $fgSizes.GetEnumerator()) {
    $dir = Join-Path $resDir $e.Key
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir | Out-Null }
    $fgPath = Join-Path $dir "ic_launcher_foreground.png"
    Create-PaddedForeground $logo $e.Value 0.18 $fgPath
}

# Generate direct drawable fallback icons
Create-CompositeLauncherIcon $logo 512 (Join-Path $resDir "drawable\ic_launcher.png") $false
Create-CompositeLauncherIcon $logo 512 (Join-Path $resDir "drawable\ic_launcher_round.png") $true

# Update all mipmap densities
$densities = @{
    "mipmap-mdpi" = 48
    "mipmap-hdpi" = 72
    "mipmap-xhdpi" = 96
    "mipmap-xxhdpi" = 144
    "mipmap-xxxhdpi" = 192
}

foreach ($d in $densities.GetEnumerator()) {
    $dir = Join-Path $resDir $d.Key
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir | Out-Null }
    Create-CompositeLauncherIcon $logo $d.Value (Join-Path $dir "ic_launcher.png") $false
    Create-CompositeLauncherIcon $logo $d.Value (Join-Path $dir "ic_launcher_round.png") $true
}

# Remove the XML ic_launcher_foreground.xml so Android uses the PNG bitmap
$xmlFg = Join-Path $resDir "drawable\ic_launcher_foreground.xml"
if (Test-Path $xmlFg) {
    Remove-Item $xmlFg -Force
    Write-Host "Removed legacy XML: $xmlFg"
}

$logo.Dispose()
Write-Host "All direct raster icons generated successfully!"
