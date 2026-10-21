import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Draws the Notes+ icon. Regenerate with:
 *   java packaging/IconGenerator.java packaging src/main/resources
 * Writes packaging/icon.ico (installer and .exe) and src/main/resources/icon.png (window and tray).
 */
public class IconGenerator {
    public static void main(String[] args) throws IOException {
        Path icoDir = Path.of(args[0]);
        Path pngDir = Path.of(args[1]);
        Files.createDirectories(icoDir);
        Files.createDirectories(pngDir);

        ImageIO.write(draw(256), "png", pngDir.resolve("icon.png").toFile());

        int[] sizes = {16, 24, 32, 48, 64, 256};
        List<byte[]> pngs = new ArrayList<>();
        for (int s : sizes) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(draw(s), "png", out);
            pngs.add(out.toByteArray());
        }
        Files.write(icoDir.resolve("icon.ico"), ico(sizes, pngs));
    }

    static BufferedImage draw(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        double s = size;

        // Blue rounded square.
        g.setPaint(new GradientPaint(0, 0, new Color(0x2B88D8), 0, (float) s, new Color(0x0063B1)));
        g.fill(new RoundRectangle2D.Double(0, 0, s, s, s * 0.44, s * 0.44));

        // White note page with a folded top-right corner.
        double left = s * 0.22, right = s * 0.68, top = s * 0.16, bottom = s * 0.84, fold = s * 0.14;
        Path2D page = new Path2D.Double();
        page.moveTo(left, top);
        page.lineTo(right - fold, top);
        page.lineTo(right, top + fold);
        page.lineTo(right, bottom);
        page.lineTo(left, bottom);
        page.closePath();
        g.setColor(Color.WHITE);
        g.fill(page);
        Path2D corner = new Path2D.Double();
        corner.moveTo(right - fold, top);
        corner.lineTo(right - fold, top + fold);
        corner.lineTo(right, top + fold);
        corner.closePath();
        g.setColor(new Color(0xC7DDF2));
        g.fill(corner);

        // Text lines (dropped at tiny sizes, where they'd be mush).
        if (size >= 24) {
            g.setColor(new Color(0x8DB8E3));
            g.setStroke(new BasicStroke((float) Math.max(1, s * 0.035), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (double y : new double[] {0.40, 0.52, 0.64}) {
                g.draw(new java.awt.geom.Line2D.Double(left + s * 0.07, s * y, right - s * 0.09, s * y));
            }
        }

        // Gold "+" badge.
        double r = s * 0.19, cx = s * 0.70, cy = s * 0.70;
        g.setColor(new Color(0x0063B1));
        g.fill(new Ellipse2D.Double(cx - r - s * 0.03, cy - r - s * 0.03, 2 * (r + s * 0.03), 2 * (r + s * 0.03)));
        g.setColor(new Color(0xFFB900));
        g.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke((float) Math.max(1.5, s * 0.06), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double arm = r * 0.55;
        g.draw(new java.awt.geom.Line2D.Double(cx - arm, cy, cx + arm, cy));
        g.draw(new java.awt.geom.Line2D.Double(cx, cy - arm, cx, cy + arm));

        g.dispose();
        return img;
    }

    /** An .ico holding PNG-compressed images (supported since Windows Vista). */
    static byte[] ico(int[] sizes, List<byte[]> pngs) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteBuffer header = ByteBuffer.allocate(6 + 16 * sizes.length).order(ByteOrder.LITTLE_ENDIAN);
        header.putShort((short) 0).putShort((short) 1).putShort((short) sizes.length);
        int offset = 6 + 16 * sizes.length;
        for (int i = 0; i < sizes.length; i++) {
            int s = sizes[i];
            header.put((byte) (s >= 256 ? 0 : s)).put((byte) (s >= 256 ? 0 : s));
            header.put((byte) 0).put((byte) 0);
            header.putShort((short) 1).putShort((short) 32);
            header.putInt(pngs.get(i).length).putInt(offset);
            offset += pngs.get(i).length;
        }
        out.write(header.array());
        for (byte[] png : pngs) out.write(png);
        return out.toByteArray();
    }
}
