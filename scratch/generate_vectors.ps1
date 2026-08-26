Add-Type -TypeDefinition @"
using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Text;

public class LogoRenderer {
    public static string GenerateVectors(string imagePath, string outSvgPath, string outXmlPath, string outPreviewPath) {
        Bitmap bmp = new Bitmap(imagePath);
        int w = bmp.Width;
        int h = bmp.Height;
        bool[,] binary = new bool[w, h];

        int minX = w, minY = h, maxX = 0, maxY = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Color c = bmp.GetPixel(x, y);
                if (c.R < 120 && c.G < 120 && c.B < 120) {
                    binary[x, y] = true;
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }

        double ew = maxX - minX + 1;
        double eh = maxY - minY + 1;

        double targetSize = 64.0;
        double scale = targetSize / Math.Max(ew, eh);
        double offsetX = (108.0 - ew * scale) / 2.0;
        double offsetY = (108.0 - eh * scale) / 2.0;

        bool[,] visited = new bool[w, h];
        List<List<Point>> allContours = new List<List<Point>>();

        int[] dx = { 0, 1, 1, 1, 0, -1, -1, -1 };
        int[] dy = { -1, -1, 0, 1, 1, 1, 0, -1 };

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (binary[x, y] && !visited[x, y]) {
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

                    if (comp.Count < 20) continue;

                    List<Point> contour = TraceComponentContour(comp, binary, w, h);
                    if (contour != null && contour.Count > 5) {
                        allContours.Add(contour);
                    }
                }
            }
        }
        bmp.Dispose();

        // Build SVG Path
        StringBuilder pathBuilder = new StringBuilder();
        List<GraphicsPath> gPaths = new List<GraphicsPath>();

        foreach (var contour in allContours) {
            List<PointF> pts = new List<PointF>();
            foreach (var p in contour) {
                double sx = offsetX + (p.X - minX) * scale;
                double sy = offsetY + (p.Y - minY) * scale;
                pts.Add(new PointF((float)sx, (float)sy));
            }
            List<PointF> simplified = RDP(pts, 0.40);
            if (simplified.Count < 3) continue;

            GraphicsPath gp = new GraphicsPath();
            gp.AddPolygon(simplified.ToArray());
            gPaths.Add(gp);

            pathBuilder.Append("M ");
            pathBuilder.AppendFormat(System.Globalization.CultureInfo.InvariantCulture, "{0:0.##},{1:0.##} ", simplified[0].X, simplified[0].Y);
            for (int i = 1; i < simplified.Count; i++) {
                pathBuilder.AppendFormat(System.Globalization.CultureInfo.InvariantCulture, "L {0:0.##},{1:0.##} ", simplified[i].X, simplified[i].Y);
            }
            pathBuilder.Append("Z ");
        }

        string fullPath = pathBuilder.ToString();

        // 1. Write SVG
        string svg = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                     "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 108 108\" width=\"108\" height=\"108\">\n" +
                     "  <rect width=\"108\" height=\"108\" rx=\"24\" fill=\"#18251F\"/>\n" +
                     "  <path fill=\"#F5F1E8\" d=\"" + fullPath + "\"/>\n" +
                     "</svg>";
        System.IO.File.WriteAllText(outSvgPath, svg, Encoding.UTF8);

        // 2. Write Android Vector Drawable XML
        string xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                     "<!-- Official Atify Authentic Emblem -->\n" +
                     "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                     "    android:width=\"108dp\"\n" +
                     "    android:height=\"108dp\"\n" +
                     "    android:viewportWidth=\"108\"\n" +
                     "    android:viewportHeight=\"108\">\n" +
                     "    <path\n" +
                     "        android:pathData=\"M54,54m-46,0a46,46 0,1 1,92 0a46,46 0,1 1,-92 0\"\n" +
                     "        android:fillColor=\"#18251F\" />\n" +
                     "    <path\n" +
                     "        android:fillColor=\"#F5F1E8\"\n" +
                     "        android:pathData=\"" + fullPath + "\" />\n" +
                     "</vector>\n";
        System.IO.File.WriteAllText(outXmlPath, xml, Encoding.UTF8);

        // 3. Render Preview PNG (512x512)
        Bitmap preview = new Bitmap(512, 512);
        using (Graphics g = Graphics.FromImage(preview)) {
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.InterpolationMode = InterpolationMode.HighQualityBicubic;
            // Clear with dark forest background
            using (Brush bgBrush = new SolidBrush(ColorTranslator.FromHtml("#18251F"))) {
                g.FillRectangle(bgBrush, 0, 0, 512, 512);
            }
            // Scale and draw paths in cream #F5F1E8
            float pScale = 512f / 108f;
            g.ScaleTransform(pScale, pScale);
            using (Brush pathBrush = new SolidBrush(ColorTranslator.FromHtml("#F5F1E8"))) {
                foreach (var gp in gPaths) {
                    g.FillPath(pathBrush, gp);
                }
            }
        }
        preview.Save(outPreviewPath, System.Drawing.Imaging.ImageFormat.Png);
        preview.Dispose();

        return fullPath;
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
        int maxIter = 5000;
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

[LogoRenderer]::GenerateVectors(
    "c:\Users\abhis\Downloads\MUSIC APP\assets\logo_emblem.png",
    "c:\Users\abhis\Downloads\MUSIC APP\assets\logo.svg",
    "c:\Users\abhis\Downloads\MUSIC APP\scratch\logo_generated.xml",
    "c:\Users\abhis\Downloads\MUSIC APP\assets\logo_preview.png"
)
Write-Host "Generated SVG, XML, and Preview PNG successfully!"
