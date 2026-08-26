Add-Type -TypeDefinition @"
using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Text;

public class SmoothVectorizer {
    // Fits smooth Catmull-Rom to Cubic Bezier for smooth vector curves
    public static string Vectorize(string imagePath, string outXmlPath, string outPreviewPath) {
        Bitmap bmp = new Bitmap(imagePath);
        int w = bmp.Width;
        int h = bmp.Height;
        bool[,] binary = new bool[w, h];

        int minX = w, minY = h, maxX = 0, maxY = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Color c = bmp.GetPixel(x, y);
                if (c.R < 130 && c.G < 130 && c.B < 130) {
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

        // Label components
        int[,] labels = new int[w, h];
        int currentLabel = 1;
        List<List<Point>> components = new List<List<Point>>();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (binary[x, y] && labels[x, y] == 0) {
                    List<Point> comp = new List<Point>();
                    Queue<Point> q = new Queue<Point>();
                    q.Enqueue(new Point(x, y));
                    labels[x, y] = currentLabel;
                    while (q.Count > 0) {
                        Point p = q.Dequeue();
                        comp.Add(p);
                        int[] dx = { 0, 1, 0, -1, 1, 1, -1, -1 };
                        int[] dy = { -1, 0, 1, 0, -1, 1, 1, -1 };
                        for (int d = 0; d < 8; d++) {
                            int nx = p.X + dx[d], ny = p.Y + dy[d];
                            if (nx >= 0 && nx < w && ny >= 0 && ny < h && binary[nx, ny] && labels[nx, ny] == 0) {
                                labels[nx, ny] = currentLabel;
                                q.Enqueue(new Point(nx, ny));
                            }
                        }
                    }
                    if (comp.Count > 25) {
                        components.Add(comp);
                        currentLabel++;
                    }
                }
            }
        }
        bmp.Dispose();

        StringBuilder svgPaths = new StringBuilder();
        List<GraphicsPath> gPaths = new List<GraphicsPath>();

        foreach (var comp in components) {
            List<Point> rawContour = GetOuterContour(comp, w, h);
            if (rawContour.Count < 6) continue;

            // Map to viewport
            List<PointF> vPts = new List<PointF>();
            foreach (var p in rawContour) {
                vPts.Add(new PointF((float)(offsetX + (p.X - minX) * scale), (float)(offsetY + (p.Y - minY) * scale)));
            }

            // Smooth the polygon with moving average
            List<PointF> smoothed = SmoothPolygon(vPts, 3);

            // Simplify with Douglas-Peucker epsilon 0.6
            List<PointF> nodes = SimplifyRDP(smoothed, 0.65);
            if (nodes.Count < 4) continue;

            // Generate cubic bezier spline through nodes
            string d = NodesToBezierPath(nodes);
            svgPaths.Append(d).Append(" ");

            GraphicsPath gp = new GraphicsPath();
            AddBezierToGraphicsPath(gp, nodes);
            gPaths.Add(gp);
        }

        string allPaths = svgPaths.ToString();

        // Write Vector XML
        string xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                     "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                     "    android:width=\"108dp\"\n" +
                     "    android:height=\"108dp\"\n" +
                     "    android:viewportWidth=\"108\"\n" +
                     "    android:viewportHeight=\"108\">\n" +
                     "    <path\n" +
                     "        android:fillColor=\"#F5F1E8\"\n" +
                     "        android:pathData=\"" + allPaths + "\" />\n" +
                     "</vector>\n";
        System.IO.File.WriteAllText(outXmlPath, xml, Encoding.UTF8);

        // Render high quality preview (512x512)
        Bitmap preview = new Bitmap(512, 512);
        using (Graphics g = Graphics.FromImage(preview)) {
            g.SmoothingMode = SmoothingMode.AntiAlias;
            using (Brush bg = new SolidBrush(ColorTranslator.FromHtml("#18251F"))) {
                g.FillRectangle(bg, 0, 0, 512, 512);
            }
            float pScale = 512f / 108f;
            g.ScaleTransform(pScale, pScale);
            using (Brush fg = new SolidBrush(ColorTranslator.FromHtml("#F5F1E8"))) {
                foreach (var gp in gPaths) {
                    g.FillPath(fg, gp);
                }
            }
        }
        preview.Save(outPreviewPath, System.Drawing.Imaging.ImageFormat.Png);
        preview.Dispose();

        return allPaths;
    }

    private static List<Point> GetOuterContour(List<Point> comp, int w, int h) {
        HashSet<long> compSet = new HashSet<long>();
        Point start = comp[0];
        foreach (var p in comp) {
            compSet.Add(((long)p.X << 32) | (uint)p.Y);
            if (p.Y < start.Y || (p.Y == start.Y && p.X < start.X)) start = p;
        }

        List<Point> contour = new List<Point>();
        Point current = start;
        int[] dx = { 0, 1, 1, 1, 0, -1, -1, -1 };
        int[] dy = { -1, -1, 0, 1, 1, 1, 0, -1 };
        int backtrack = 7;
        int maxIter = 5000, iter = 0;

        do {
            contour.Add(current);
            int nextDir = -1;
            for (int i = 0; i < 8; i++) {
                int check = (backtrack + 1 + i) % 8;
                int nx = current.X + dx[check], ny = current.Y + dy[check];
                if (nx >= 0 && nx < w && ny >= 0 && ny < h && compSet.Contains(((long)nx << 32) | (uint)ny)) {
                    nextDir = check;
                    break;
                }
            }
            if (nextDir == -1) break;
            current = new Point(current.X + dx[nextDir], current.Y + dy[nextDir]);
            backtrack = (nextDir + 4) % 8;
            iter++;
        } while ((current.X != start.X || current.Y != start.Y) && iter < maxIter);

        return contour;
    }

    private static List<PointF> SmoothPolygon(List<PointF> pts, int passes) {
        List<PointF> result = new List<PointF>(pts);
        int n = pts.Count;
        for (int p = 0; p < passes; p++) {
            List<PointF> next = new List<PointF>(n);
            for (int i = 0; i < n; i++) {
                PointF prev = result[(i - 1 + n) % n];
                PointF curr = result[i];
                PointF fwd = result[(i + 1) % n];
                next.Add(new PointF((prev.X + 2 * curr.X + fwd.X) / 4f, (prev.Y + 2 * curr.Y + fwd.Y) / 4f));
            }
            result = next;
        }
        return result;
    }

    private static List<PointF> SimplifyRDP(List<PointF> pts, double epsilon) {
        if (pts.Count < 3) return new List<PointF>(pts);
        int maxIdx = 0;
        double maxDist = 0;
        PointF first = pts[0], last = pts[pts.Count - 1];
        for (int i = 1; i < pts.Count - 1; i++) {
            double d = Math.Abs((last.Y - first.Y) * pts[i].X - (last.X - first.X) * pts[i].Y + last.X * first.Y - last.Y * first.X) /
                       Math.Sqrt((last.Y - first.Y) * (last.Y - first.Y) + (last.X - first.X) * (last.X - first.X));
            if (d > maxDist) { maxDist = d; maxIdx = i; }
        }
        if (maxDist > epsilon) {
            var left = SimplifyRDP(pts.GetRange(0, maxIdx + 1), epsilon);
            var right = SimplifyRDP(pts.GetRange(maxIdx, pts.Count - maxIdx), epsilon);
            left.RemoveAt(left.Count - 1);
            left.AddRange(right);
            return left;
        }
        return new List<PointF> { first, last };
    }

    private static string NodesToBezierPath(List<PointF> pts) {
        int n = pts.Count;
        StringBuilder sb = new StringBuilder();
        sb.AppendFormat(System.Globalization.CultureInfo.InvariantCulture, "M {0:0.##},{1:0.##} ", pts[0].X, pts[0].Y);
        for (int i = 0; i < n; i++) {
            PointF p0 = pts[(i - 1 + n) % n];
            PointF p1 = pts[i];
            PointF p2 = pts[(i + 1) % n];
            PointF p3 = pts[(i + 2) % n];

            // Catmull-Rom to Cubic Bezier control points
            float tension = 0.22f; // smooth curvature
            PointF cp1 = new PointF(p1.X + (p2.X - p0.X) * tension, p1.Y + (p2.Y - p0.Y) * tension);
            PointF cp2 = new PointF(p2.X - (p3.X - p1.X) * tension, p2.Y - (p3.Y - p1.Y) * tension);

            sb.AppendFormat(System.Globalization.CultureInfo.InvariantCulture,
                "C {0:0.##},{1:0.##} {2:0.##},{3:0.##} {4:0.##},{5:0.##} ",
                cp1.X, cp1.Y, cp2.X, cp2.Y, p2.X, p2.Y);
        }
        sb.Append("Z");
        return sb.ToString();
    }

    private static void AddBezierToGraphicsPath(GraphicsPath gp, List<PointF> pts) {
        int n = pts.Count;
        for (int i = 0; i < n; i++) {
            PointF p0 = pts[(i - 1 + n) % n];
            PointF p1 = pts[i];
            PointF p2 = pts[(i + 1) % n];
            PointF p3 = pts[(i + 2) % n];
            float tension = 0.22f;
            PointF cp1 = new PointF(p1.X + (p2.X - p0.X) * tension, p1.Y + (p2.Y - p0.Y) * tension);
            PointF cp2 = new PointF(p2.X - (p3.X - p1.X) * tension, p2.Y - (p3.Y - p1.Y) * tension);
            gp.AddBezier(p1, cp1, cp2, p2);
        }
    }
}
"@ -ReferencedAssemblies System.Drawing

[SmoothVectorizer]::Vectorize(
    "c:\Users\abhis\Downloads\MUSIC APP\assets\logo_emblem.png",
    "c:\Users\abhis\Downloads\MUSIC APP\scratch\logo_smooth.xml",
    "c:\Users\abhis\Downloads\MUSIC APP\assets\logo_smooth_preview.png"
)
Write-Host "Vectorized smoothly!"
