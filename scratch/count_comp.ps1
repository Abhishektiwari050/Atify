Add-Type -AssemblyName System.Drawing
$bmp = [System.Drawing.Bitmap]::FromFile("c:\Users\abhis\Downloads\MUSIC APP\assets\logo_hires.png")
$w = $bmp.Width
$h = $bmp.Height

# Label 8-connected components
$labels = New-Object 'int[,]' $w, $h
$currentLabel = 1
$counts = @{}

for ($y=0; $y -lt $h; $y++) {
    for ($x=0; $x -lt $w; $x++) {
        $c = $bmp.GetPixel($x, $y)
        if ($c.R -lt 135 -and $c.G -lt 135 -and $c.B -lt 135 -and $labels[$x, $y] -eq 0) {
            $q = New-Object System.Collections.Queue
            $q.Enqueue([System.Drawing.Point]::new($x, $y))
            $labels[$x, $y] = $currentLabel
            $cnt = 0
            while ($q.Count -gt 0) {
                $p = $q.Dequeue()
                $cnt++
                $dx = @(0, 1, 0, -1, 1, 1, -1, -1)
                $dy = @(-1, 0, 1, 0, -1, 1, 1, -1)
                for ($d=0; $d -lt 8; $d++) {
                    $nx = $p.X + $dx[$d]
                    $ny = $p.Y + $dy[$d]
                    if ($nx -ge 0 -and $nx -lt $w -and $ny -ge 0 -and $ny -lt $h -and $labels[$nx, $ny] -eq 0) {
                        $nc = $bmp.GetPixel($nx, $ny)
                        if ($nc.R -lt 135 -and $nc.G -lt 135 -and $nc.B -lt 135) {
                            $labels[$nx, $ny] = $currentLabel
                            $q.Enqueue([System.Drawing.Point]::new($nx, $ny))
                        }
                    }
                }
            }
            $counts[$currentLabel] = $cnt
            $currentLabel++
        }
    }
}

Write-Host "Found $($counts.Count) components:"
$counts.GetEnumerator() | Sort-Object Value -Descending | ForEach-Object {
    Write-Host "Label $($_.Key): $($_.Value) pixels"
}
$bmp.Dispose()
