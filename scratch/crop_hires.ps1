Add-Type -AssemblyName System.Drawing
$srcPath = "C:\Users\abhis\.gemini\antigravity\brain\4c6fb92f-dc3e-4c38-95b0-44271b112de6\.user_uploaded\media_1787722035811.png"
$img = [System.Drawing.Image]::FromFile($srcPath)
$w = $img.Width
$h = $img.Height

# Crop LOGO USAGE emblem (x: 0.77 to 0.93, y: 0.12 to 0.35)
$x1 = [int](0.78 * $w)
$y1 = [int](0.13 * $h)
$w1 = [int](0.14 * $w)
$h1 = [int](0.20 * $h)

$rect = New-Object System.Drawing.Rectangle $x1, $y1, $w1, $h1
$bmp = New-Object System.Drawing.Bitmap $rect.Width, $rect.Height
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.DrawImage($img, 0, 0, $rect, [System.Drawing.GraphicsUnit]::Pixel)

# Save upscale 4x
$upW = $rect.Width * 4
$upH = $rect.Height * 4
$upBmp = New-Object System.Drawing.Bitmap $upW, $upH
$gUp = [System.Drawing.Graphics]::FromImage($upBmp)
$gUp.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$gUp.DrawImage($bmp, 0, 0, $upW, $upH)
$upBmp.Save("c:\Users\abhis\Downloads\MUSIC APP\assets\logo_hires.png", [System.Drawing.Imaging.ImageFormat]::Png)

$gUp.Dispose()
$upBmp.Dispose()
$g.Dispose()
$bmp.Dispose()
$img.Dispose()
Write-Host "Saved logo_hires.png"
