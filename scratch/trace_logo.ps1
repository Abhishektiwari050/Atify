Add-Type -TypeDefinition @"
using System;
using System.Collections.Generic;
using System.Drawing;
using System.Text;

public class LogoVectorizer {
    public static string TraceLogo(string imagePath) {
        Bitmap bmp = new Bitmap(imagePath);
        int w = bmp.Width;
        int h = bmp.Height;
        bool[,] binary = new bool[w, h];

        // 1. Threshold
        int minX = w, minY = h, maxX = 0, maxY = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Color c = bmp.GetPixel(x, y);
                // Background is ~ (245, 241, 232). Dark shape is ~ (24, 37, 31).
                // Luminance / threshold
                if (c.R < 120 && c.G < 120 && c.B < 120) {
                    binary[x, y] = true;
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }

        // Bounding box dimensions
        double ew = maxX - minX + 1;
        double eh = maxY - minY + 1;

        // Target viewport: 108x108, with margin (e.g. fit in 20..88, height ~68)
        double targetSize = 72.0;
        double scale = targetSize / Math.Max(ew, eh);
        double offsetX = (108.0 - ew * scale) / 2.0;
        double offsetY = (108.0 - eh * scale) / 2.0;

        // 2. Find connected components and trace outer contour with Moore-Neighbor
        bool[,] visited = new bool[w, h];
        List<List<Point>> allContours = new List<List<Point>>();

        int[] dx = { 0, 1, 1, 1, 0, -1, -1, -1 };
        int[] dy = { -1, -1, 0, 1, 1, 1, 0, -1 };

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (binary[x, y] && !visited[x, y]) {
                    // Flood fill this component to mark visited
                    List<Point> comp = new List<Point>();
                    Queue<Point> q = new Queue<Point>();
                    q.Enqueue(new Point(x, y));
                    visited[x, y] = true;
                    while (q.Count > 0) {
                        Point p = q.Dequeue();
                        comp.Add(p);
                        for (int d = 0; d < 8; d++) {
                            int nx = p.X + dx[d], ny = p.Y + dy[d];
                            if (nx >= 0 && nx < w && ny >= 0 && ny < h && binary[nx, ny] && !visited[nx, ny]) {
                                visited[nx, ny] = true;
                                q.Enqueue(new Point(nx, ny));
                            }
                        }
                    }

                    // Ignore tiny noise (< 20 pixels)
                    if (comp.Count < 20) continue;

                    // Trace contour for this component
                    List<Point> contour = TraceComponentContour(comp, binary, w, h);
                    if (contour != null && contour.Count > 5) {
                        allContours.Add(contour);
                    }
                }
            }
        }

        bmp.Dispose();

        // Convert contours to SVG path string
        StringBuilder sb = new StringBuilder();
        foreach (var contour in allContours) {
            // Simplify contour using Ramer-Douglas-Peucker
            List<PointF> pts = new List<PointF>();
            foreach (var p in contour) {
                double sx = offsetX + (p.X - minX) * scale;
                double sy = offsetY + (p.Y - minY) * scale;
                pts.Add(new PointF((float)sx, (float)sy));
            }
            List<PointF> simplified = RDP(pts, 0.45); // epsilon 0.45 for smooth crisp curves

            if (simplified.Count < 3) continue;

            sb.Append("M ");
            sb.AppendFormat(System.Globalization.CultureInfo.InvariantCulture, "{0:0.##},{1:0.##} ", simplified[0].X, simplified[0].Y);
            for (int i = 1; i < simplified.Count; i++) {
                sb.AppendFormat(System.Globalization.CultureInfo.InvariantCulture, "L {0:0.##},{1:0.##} ", simplified[i].X, simplified[i].Y);
            }
            sb.Append("Z ");
        }

        return sb.ToString();
    }

    private static List<Point> TraceComponentContour(List<Point> comp, bool[,] binary, int w, int h) {
        HashSet<long> compSet = new HashSet<long>();
        Point start = comp[0];
        foreach (var p in comp) {
            compSet.Add(((long)p.X << 32) | (uint)p.Y);
            if (p.Y < start.Y || (p.Y == start.Y && p.X < start.X)) {
                start = p;
            }
        }

        List<Point> contour = new List<Point>();
        Point current = start;
        int[] dx = { 0, 1, 1, 1, 0, -1, -1, -1 };
        int[] dy = { -1, -1, 0, 1, 1, 1, 0, -1 };

        int backtrackDir = 7;
        int maxIter = 4000;
        int iter = 0;

        do {
            contour.Add(current);
            int nextDir = -1;
            for (int i = 0; i < 8; i++) {
                int checkDir = (backtrackDir + 1 + i) % 8;
                int nx = current.X + dx[checkDir];
                int ny = current.Y + dy[checkDir];
                if (nx >= 0 && nx < w && ny >= 0 && ny < h && compSet.Contains(((long)nx << 32) | (uint)ny)) {
                    nextDir = checkDir;
                    break;
                }
            }

            if (nextDir == -1) break;

            current = new Point(current.X + dx[nextDir], current.Y + dy[nextDir]);
            backtrackDir = (nextDir + 4) % 8;
            iter++;
        } while ((current.X != start.X || current.Y != start.Y) && iter < maxIter);

        return contour;
    }

    private static List<PointF> RDP(List<PointF> points, double epsilon) {
        if (points.Count < 3) return new List<PointF>(points);

        int maxIdx = 0;
        double maxDist = 0;
        PointF first = points[0];
        PointF last = points[points.Count - 1];

        for (int i = 1; i < points.Count - 1; i++) {
            double d = PerpendicularDistance(points[i], first, last);
            if (d > maxDist) {
                maxDist = d;
                maxIdx = i;
            }
        }

        if (maxDist > epsilon) {
            List<PointF> left = RDP(points.GetRange(0, maxIdx + 1), epsilon);
            List<PointF> right = RDP(points.GetRange(maxIdx, points.Count - maxIdx), epsilon);
            left.RemoveAt(left.Count - 1);
            left.AddRange(right);
            return left;
        } else {
            return new List<PointF> { first, last };
        }
    }

    private static double PerpendicularDistance(PointF pt, PointF lineStart, PointF lineEnd) {
        double dx = lineEnd.X - lineStart.X;
        double dy = lineEnd.Y - lineStart.Y;
        double len = Math.Sqrt(dx * dx + dy * dy);
        if (len == 0) return Math.Sqrt((pt.X - lineStart.X) * (pt.X - lineStart.X) + (pt.Y - lineStart.Y) * (pt.Y - lineStart.Y));
        return Math.Abs(dy * pt.X - dx * pt.Y + lineEnd.X * lineStart.Y - lineEnd.Y * lineStart.X) / len;
    }
}
"@ -ReferencedAssemblies System.Drawing

$pathData = [LogoVectorizer]::TraceLogo("c:\Users\abhis\Downloads\MUSIC APP\assets\logo_emblem.png")
Write-Host "Generated Path Data Length: $($pathData.Length)"
$pathData | Out-File -FilePath "c:\Users\abhis\Downloads\MUSIC APP\scratch\logo_path_data.txt"
Write-Host "Saved to scratch\logo_path_data.txt"
