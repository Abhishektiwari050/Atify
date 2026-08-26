Add-Type -TypeDefinition @"
using System;
using System.Drawing;
using System.Drawing.Drawing2D;

public class LogoTuner {
    public static void RenderComparison(string outXmlPath, string outPreviewPath) {
        int size = 512;
        Bitmap preview = new Bitmap(size, size);
        using (Graphics g = Graphics.FromImage(preview)) {
            g.SmoothingMode = SmoothingMode.AntiAlias;
            using (Brush bg = new SolidBrush(ColorTranslator.FromHtml("#18251F"))) {
                g.FillRectangle(bg, 0, 0, size, size);
            }

            float scale = size / 108f;
            g.ScaleTransform(scale, scale);

            // Let's define the 4 smooth hand-crafted Bezier paths in 108x108 viewport
            GraphicsPath gp = new GraphicsPath();

            // Element 1: Outer sweeping arch with bottom-left loop and top wave crest
            // Outer left: (28, 70) -> (36, 46) -> (50, 26) -> (58, 26) -> (66, 34)
            // Crest point: (66, 34)
            // Under crest: (58, 35) -> (52, 36) -> (42, 50) -> (34, 64)
            // Bottom loop: (34, 64) -> (28, 72) -> (24, 70) -> (24, 66) -> (28, 54) -> (36, 40)
            
            // Element 1:
            PointF[] p1 = new PointF[] {
                new PointF(24f, 68f),
                new PointF(24f, 63f), new PointF(32f, 44f), new PointF(46f, 27f),
                new PointF(52f, 25f), new PointF(61f, 25f), new PointF(66f, 33f), // Crest tip
                new PointF(64f, 35f), new PointF(58f, 34.5f), new PointF(52f, 35.5f),
                new PointF(45f, 42f), new PointF(38f, 52f), new PointF(31f, 65f), // Inner leg
                new PointF(28f, 71f), new PointF(24f, 71f), new PointF(24f, 68f)  // Bottom loop
            };

            // Element 2 (Upper middle wave):
            PointF[] p2 = new PointF[] {
                new PointF(35f, 62f), // Left sharp tip
                new PointF(39f, 52f), new PointF(50f, 40f), new PointF(62f, 41f),
                new PointF(66f, 43f), new PointF(70f, 47f), new PointF(72f, 53f), // Right sharp tip
                new PointF(69f, 50f), new PointF(60f, 47f), new PointF(50f, 49f),
                new PointF(42f, 55f), new PointF(38f, 59f), new PointF(35f, 62f)
            };

            // Element 3 (Lower middle wave):
            PointF[] p3 = new PointF[] {
                new PointF(41f, 68f), // Left tip
                new PointF(46f, 60f), new PointF(55f, 53f), new PointF(66f, 55f),
                new PointF(73f, 59f), new PointF(78f, 66f), new PointF(78f, 72f), // Bottom right rounded
                new PointF(77f, 73f), new PointF(73f, 71f), new PointF(68f, 65f),
                new PointF(61f, 60f), new PointF(52f, 61f), new PointF(41f, 68f)
            };

            // Element 4 (Base crescent):
            PointF[] p4 = new PointF[] {
                new PointF(50f, 68.5f),
                new PointF(54f, 65f), new PointF(63f, 65f), new PointF(68f, 69.5f),
                new PointF(65f, 68f), new PointF(56f, 67f), new PointF(50f, 68.5f)
            };

            // Draw them in Cream #F5F1E8
            using (Brush fg = new SolidBrush(ColorTranslator.FromHtml("#F5F1E8"))) {
                DrawBeziers(g, fg, p1);
                DrawBeziers(g, fg, p2);
                DrawBeziers(g, fg, p3);
                DrawBeziers(g, fg, p4);
            }
        }
        preview.Save(outPreviewPath, System.Drawing.Imaging.ImageFormat.Png);
        preview.Dispose();
    }

    private static void DrawBeziers(Graphics g, Brush b, PointF[] pts) {
        GraphicsPath gp = new GraphicsPath();
        gp.StartFigure();
        gp.AddBeziers(pts);
        gp.CloseFigure();
        g.FillPath(b, gp);
    }
}
"@ -ReferencedAssemblies System.Drawing

[LogoTuner]::RenderComparison(
    "c:\Users\abhis\Downloads\MUSIC APP\scratch\logo_tuned.xml",
    "c:\Users\abhis\Downloads\MUSIC APP\assets\logo_tuned_preview.png"
)
Write-Host "Rendered tuned logo preview!"
