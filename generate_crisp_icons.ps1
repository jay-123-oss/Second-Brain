Add-Type -AssemblyName System.Drawing

$srcPath = "C:\Users\jayde\.gemini\antigravity-ide\brain\1065597f-a2e4-44b7-87d2-5e6e8ed3bd8a\.user_uploaded\media_1788639367261.png"
$resDir = "c:\Users\jayde\OneDrive\Desktop\Brain\app\src\main\res"

# Load original 64x64 image
$srcBmp = New-Object System.Drawing.Bitmap($srcPath)
Write-Host "Loaded source image: $($srcBmp.Width) x $($srcBmp.Height)"

# We will generate a master 1024x1024 bitmap using 16x scaling with smoothing
$masterSize = 1024
$masterBmp = New-Object System.Drawing.Bitmap($masterSize, $masterSize, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($masterBmp)
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
$g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
$g.Clear([System.Drawing.Color]::Transparent)

# Draw image scaled up
$g.DrawImage($srcBmp, 0, 0, $masterSize, $masterSize)
$g.Dispose()

# Now threshold/clean the alpha channel to make the black edges super crisp and smooth
$cleanMaster = New-Object System.Drawing.Bitmap($masterSize, $masterSize, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
for ($y = 0; $y -lt $masterSize; $y++) {
    for ($x = 0; $x -lt $masterSize; $x++) {
        $p = $masterBmp.GetPixel($x, $y)
        if ($p.A -gt 0) {
            # Ensure color is pure deep black #0F172A or #000000 with preserved smooth anti-aliased alpha
            $alpha = $p.A
            # Enhance edge sharpness while keeping anti-aliasing
            if ($alpha -gt 180) {
                $alpha = 255
            } elseif ($alpha -lt 40) {
                $alpha = 0
            } else {
                $alpha = [int](($alpha - 40) / 140.0 * 255.0)
            }
            if ($alpha -gt 0) {
                $cleanMaster.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($alpha, 15, 23, 42))
            } else {
                $cleanMaster.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(0, 0, 0, 0))
            }
        }
    }
}
$masterBmp.Dispose()

# Save master high-res drawable
$masterDrawable = Join-Path $resDir "drawable\ic_app_logo.png"
$cleanMaster.Save($masterDrawable, [System.Drawing.Imaging.ImageFormat]::Png)
Write-Host "Saved high-res logo: $masterDrawable (1024x1024)"

# Also save in drawable-xxxhdpi and drawable-xxhdpi if needed
$drawableXxx = Join-Path $resDir "drawable-xxxhdpi"
if (-not (Test-Path $drawableXxx)) { New-Item -ItemType Directory -Path $drawableXxx | Out-Null }
$cleanMaster.Save((Join-Path $drawableXxx "ic_app_logo.png"), [System.Drawing.Imaging.ImageFormat]::Png)

# Function to generate launcher icon with clean white background and centered logo
function Generate-LauncherIcon($logoBmp, $size, $outPath, $isRound) {
    $iconBmp = New-Object System.Drawing.Bitmap($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $ig = [System.Drawing.Graphics]::FromImage($iconBmp)
    $ig.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $ig.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $ig.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $ig.Clear([System.Drawing.Color]::Transparent)

    $whiteBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)
    
    if ($isRound) {
        # Circular background
        $ig.FillEllipse($whiteBrush, 1, 1, $size - 2, $size - 2)
    } else {
        # Rounded squircle background
        $radius = [int]($size * 0.22)
        $rect = New-Object System.Drawing.Rectangle(0, 0, $size, $size)
        $path = New-Object System.Drawing.Drawing2D.GraphicsPath
        $diameter = $radius * 2
        $path.AddArc($rect.X, $rect.Y, $diameter, $diameter, 180, 90)
        $path.AddArc($rect.Right - $diameter, $rect.Y, $diameter, $diameter, 270, 90)
        $path.AddArc($rect.Right - $diameter, $rect.Bottom - $diameter, $diameter, $diameter, 0, 90)
        $path.AddArc($rect.X, $rect.Bottom - $diameter, $diameter, $diameter, 90, 90)
        $path.CloseFigure()
        $ig.FillPath($whiteBrush, $path)
        $path.Dispose()
    }

    # Inset logo inside background (inset ~14% on each side)
    $logoMargin = [int]($size * 0.12)
    $logoDrawSize = $size - ($logoMargin * 2)
    $ig.DrawImage($logoBmp, $logoMargin, $logoMargin, $logoDrawSize, $logoDrawSize)

    $ig.Dispose()
    $whiteBrush.Dispose()

    $iconBmp.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $iconBmp.Dispose()
    Write-Host "Generated launcher icon: $outPath ($size x $size)"
}

# Generate all mipmap launcher icons
$densities = @{
    "mipmap-mdpi" = 48
    "mipmap-hdpi" = 72
    "mipmap-xhdpi" = 96
    "mipmap-xxhdpi" = 144
    "mipmap-xxxhdpi" = 192
}

foreach ($d in $densities.GetEnumerator()) {
    $folder = Join-Path $resDir $d.Key
    if (-not (Test-Path $folder)) { New-Item -ItemType Directory -Path $folder | Out-Null }
    
    $squarePath = Join-Path $folder "ic_launcher.png"
    $roundPath = Join-Path $folder "ic_launcher_round.png"
    
    Generate-LauncherIcon $cleanMaster $d.Value $squarePath $false
    Generate-LauncherIcon $cleanMaster $d.Value $roundPath $true
}

$cleanMaster.Dispose()
$srcBmp.Dispose()
Write-Host "Logo generation completed successfully!"
