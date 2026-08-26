Add-Type -AssemblyName System.Drawing
$bmp = [System.Drawing.Bitmap]::FromFile("c:\Users\abhis\Downloads\MUSIC APP\assets\logo_emblem.png")
$w = $bmp.Width
$h = $bmp.Height

Write-Host "Image size: $w x $h"

# Find bounding box of dark pixels
$minX = $w; $minY = $h; $maxX = 0; $maxY = 0

for ($y = 0; $y -lt $h; $y++) {
    for ($x = 0; $x -lt $w; $x++) {
        $c = $bmp.GetPixel($x, $y)
        # Background is high brightness (R > 200, G > 200, B > 180)
        # Dark emblem is R < 80, G < 80, B < 80
        if ($c.R -lt 100 -and $c.G -lt 100 -and $c.B -lt 100) {
            if ($x -lt $minX) { $minX = $x }
            if ($x -gt $maxX) { $maxX = $x }
            if ($y -lt $minY) { $minY = $y }
            if ($y -gt $maxY) { $maxY = $y }
        }
    }
}

Write-Host "Emblem bounding box: X=[$minX, $maxX], Y=[$minY, $maxY]"
$ew = $maxX - $minX + 1
$eh = $maxY - $minY + 1
Write-Host "Emblem width: $ew, height: $eh"

$bmp.Dispose()
