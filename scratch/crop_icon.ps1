Add-Type -AssemblyName System.Drawing
$srcPath = "C:\Users\abhis\.gemini\antigravity\brain\4c6fb92f-dc3e-4c38-95b0-44271b112de6\.user_uploaded\media_1787722035811.png"
$img = [System.Drawing.Image]::FromFile($srcPath)
$w = $img.Width
$h = $img.Height

# Crop Dark App Icon in APP ICON PREVIEW (x: ~48% to 56%, y: ~10% to 26%)
$x1 = [int](0.485 * $w)
$y1 = [int](0.12 * $h)
$w1 = [int](0.075 * $w)
$h1 = [int](0.14 * $h)

$rect = New-Object System.Drawing.Rectangle $x1, $y1, $w1, $h1
$bmp = New-Object System.Drawing.Bitmap $rect.Width, $rect.Height
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.DrawImage($img, 0, 0, $rect, [System.Drawing.GraphicsUnit]::Pixel)
$bmp.Save("c:\Users\abhis\Downloads\MUSIC APP\assets\app_icon_dark.png", [System.Drawing.Imaging.ImageFormat]::Png)

$g.Dispose()
$bmp.Dispose()
$img.Dispose()
Write-Host "Saved app_icon_dark.png"
