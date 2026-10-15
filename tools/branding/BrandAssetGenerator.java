import java.awt.Color;
import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.RoundRectangle2D;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * Rebuilds the Dot Dialer production brand assets from the approved raster reference.
 *
 * <p>The tool intentionally uses only the JDK so the vector master and Android raster
 * fallbacks can be regenerated without a design application or third-party dependency.</p>
 */
public final class BrandAssetGenerator {
    private static final double VIEWPORT = 108.0;
    private static final double SAFE_RADIUS = 33.0;
    private static final double TARGET_RADIUS = 31.0;
    private static final double LAUNCHER_MARK_SCALE = 0.84;
    private static final double SIMPLIFY_TOLERANCE_PX = 1.35;
    private static final Color JADE = new Color(0x00, 0xBB, 0x77);
    private static final Color WHITE = Color.WHITE;

    private record Point(double x, double y) {
        double distanceSquared(Point other) {
            double dx = x - other.x;
            double dy = y - other.y;
            return dx * dx + dy * dy;
        }
    }

    private BrandAssetGenerator() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "Usage: BrandAssetGenerator <approved-phone-mark.png> <repository-root>");
        }

        Path source = Path.of(args[0]).toAbsolutePath().normalize();
        Path root = Path.of(args[1]).toAbsolutePath().normalize();
        BufferedImage reference = ImageIO.read(source.toFile());
        if (reference == null) {
            throw new IOException("Cannot read " + source);
        }

        boolean[][] mask = buildWhiteMarkMask(reference);
        List<Point> contour = traceOuterContour(mask);
        List<Point> simplified = simplifyClosed(contour, SIMPLIFY_TOLERANCE_PX);
        List<Point> rawViewportContour = scaleToViewport(
                contour, reference.getWidth(), reference.getHeight(), 1.0);
        double safeScale = Math.min(1.0, TARGET_RADIUS / maxRadius(rawViewportContour));
        List<Point> viewportPoints = scaleToViewport(
                simplified, reference.getWidth(), reference.getHeight(), safeScale);
        List<Point> fullViewportContour = scaleToViewport(
                contour, reference.getWidth(), reference.getHeight(), safeScale);
        validateSafeZone(fullViewportContour);

        String pathData = buildSmoothPathData(viewportPoints);
        List<Point> launcherViewportPoints = scaleAroundViewportCenter(
                viewportPoints, LAUNCHER_MARK_SCALE);
        List<Point> fullLauncherViewportContour = scaleAroundViewportCenter(
                fullViewportContour, LAUNCHER_MARK_SCALE);
        String launcherPathData = buildSmoothPathData(launcherViewportPoints);
        writeText(root.resolve("branding/dot-dialer-phone-mark.svg"), phoneMarkSvg(pathData));
        writeText(root.resolve("branding/dot-dialer-mark.svg"), jadeMarkSvg(pathData));
        writeText(root.resolve("branding/dot-dialer-app-icon.svg"), appIconSvg(launcherPathData));
        writeText(root.resolve("branding/dot-dialer-icon-squircle.svg"), appIconSvg(launcherPathData));
        Shape wordmarkOutline = buildWordmarkOutline(root);
        String wordmarkPath = shapeToSvgPath(wordmarkOutline);
        writeText(
                root.resolve("branding/dot-dialer-logo-lockup.svg"),
                logoLockupSvg(pathData, wordmarkPath));
        writeText(
                root.resolve("app/src/main/res/drawable/ic_dot_dialer_foreground.xml"),
                androidVector(launcherPathData));
        writeText(
                root.resolve("app/src/main/res/drawable/ic_dot_dialer_monochrome.xml"),
                androidVector(launcherPathData));
        writeText(
                root.resolve("app/src/main/res/drawable/ic_dot_dialer_about.xml"),
                androidAppIconVector(launcherPathData));
        writeText(
                root.resolve("app/src/main/res/drawable/ic_dot_dialer_notification.xml"),
                androidNotificationVector(pathData));
        writeText(
                root.resolve("app/src/main/res/drawable/ic_widget_call.xml"),
                androidActionVector(pathData, 0.0));
        writeText(
                root.resolve("app/src/main/res/drawable/ic_widget_phone.xml"),
                androidActionVector(pathData, 0.0));
        writeText(
                root.resolve("app/src/main/res/drawable/ic_phone.xml"),
                androidActionVector(pathData, 0.0));
        writeText(
                root.resolve("app/src/main/res/drawable/ic_phone_star.xml"),
                androidActionVectorWithStar(pathData));
        writeText(
                root.resolve("app/src/main/java/dev/goodwy/rphone/view/components/DotDialerIcons.kt"),
                composeBrandIcons(pathData));

        renderDocumentationPreview(
                root.resolve("branding/dot-dialer-icon-squircle-render.png"),
                launcherViewportPoints,
                1024);
        renderLogoLockup(
                root.resolve("branding/dot-dialer-logo-lockup-render.png"),
                viewportPoints,
                wordmarkOutline);
        renderLauncherAssets(root, launcherViewportPoints);

        Bounds bounds = bounds(fullViewportContour);
        double maxRadius = maxRadius(fullViewportContour);
        Bounds launcherBounds = bounds(fullLauncherViewportContour);
        double launcherMaxRadius = maxRadius(fullLauncherViewportContour);
        System.out.printf(
                Locale.ROOT,
                "Generated %d-segment vector; master bounds %.2f,%.2f..%.2f,%.2f; "
                        + "master radius %.2f/%.2f; launcher bounds %.2f,%.2f..%.2f,%.2f; "
                        + "launcher radius %.2f%n",
                viewportPoints.size(),
                bounds.minX,
                bounds.minY,
                bounds.maxX,
                bounds.maxY,
                maxRadius,
                SAFE_RADIUS,
                launcherBounds.minX,
                launcherBounds.minY,
                launcherBounds.maxX,
                launcherBounds.maxY,
                launcherMaxRadius);
    }

    private static boolean[][] buildWhiteMarkMask(BufferedImage image) {
        boolean[][] mask = new boolean[image.getHeight()][image.getWidth()];
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                int red = (rgb >>> 16) & 0xFF;
                int green = (rgb >>> 8) & 0xFF;
                int blue = rgb & 0xFF;
                int maximum = Math.max(red, Math.max(green, blue));
                int minimum = Math.min(red, Math.min(green, blue));
                mask[y][x] = red >= 218
                        && green >= 218
                        && blue >= 218
                        && maximum - minimum <= 24;
            }
        }
        return mask;
    }

    private static List<Point> traceOuterContour(boolean[][] mask) {
        int height = mask.length;
        int width = mask[0].length;
        int startX = -1;
        int startY = -1;
        for (int y = 0; y < height && startX < 0; y++) {
            for (int x = 0; x < width; x++) {
                if (mask[y][x]) {
                    startX = x;
                    startY = y;
                    break;
                }
            }
        }
        if (startX < 0) {
            throw new IllegalStateException("Approved reference contains no white mark");
        }

        // Clockwise Moore neighbourhood: E, SE, S, SW, W, NW, N, NE.
        int[][] directions = {
                {1, 0}, {1, 1}, {0, 1}, {-1, 1},
                {-1, 0}, {-1, -1}, {0, -1}, {1, -1}
        };
        int currentX = startX;
        int currentY = startY;
        int backX = startX - 1;
        int backY = startY;
        Point firstNext = null;
        List<Point> result = new ArrayList<>();
        int guard = width * height * 4;

        while (guard-- > 0) {
            int backIndex = directionIndex(backX - currentX, backY - currentY, directions);
            int foundIndex = -1;
            for (int offset = 1; offset <= directions.length; offset++) {
                int candidateIndex = (backIndex + offset) % directions.length;
                int candidateX = currentX + directions[candidateIndex][0];
                int candidateY = currentY + directions[candidateIndex][1];
                if (candidateX >= 0
                        && candidateX < width
                        && candidateY >= 0
                        && candidateY < height
                        && mask[candidateY][candidateX]) {
                    foundIndex = candidateIndex;
                    break;
                }
            }
            if (foundIndex < 0) {
                throw new IllegalStateException("The approved mark is not a connected shape");
            }

            int nextX = currentX + directions[foundIndex][0];
            int nextY = currentY + directions[foundIndex][1];
            Point next = new Point(nextX, nextY);
            if (firstNext != null
                    && currentX == startX
                    && currentY == startY
                    && next.equals(firstNext)
                    && result.size() > 2) {
                break;
            }

            result.add(new Point(currentX, currentY));
            if (firstNext == null) {
                firstNext = next;
            }
            int previousIndex = (foundIndex + directions.length - 1) % directions.length;
            backX = currentX + directions[previousIndex][0];
            backY = currentY + directions[previousIndex][1];
            currentX = nextX;
            currentY = nextY;
        }

        if (guard <= 0 || result.size() < 32) {
            throw new IllegalStateException("Could not trace a stable outer contour");
        }
        return result;
    }

    private static int directionIndex(int dx, int dy, int[][] directions) {
        for (int index = 0; index < directions.length; index++) {
            if (directions[index][0] == dx && directions[index][1] == dy) {
                return index;
            }
        }
        throw new IllegalStateException("Backtrack point is not in the Moore neighbourhood");
    }

    private static List<Point> simplifyClosed(List<Point> points, double tolerance) {
        Point anchor = points.get(0);
        int farthestIndex = 1;
        double farthestDistance = 0;
        for (int index = 1; index < points.size(); index++) {
            double distance = points.get(index).distanceSquared(anchor);
            if (distance > farthestDistance) {
                farthestDistance = distance;
                farthestIndex = index;
            }
        }

        List<Point> firstArc = new ArrayList<>(points.subList(0, farthestIndex + 1));
        List<Point> secondArc = new ArrayList<>(points.subList(farthestIndex, points.size()));
        secondArc.add(anchor);
        List<Point> first = simplifyOpen(firstArc, tolerance);
        List<Point> second = simplifyOpen(secondArc, tolerance);

        List<Point> combined = new ArrayList<>(first);
        combined.remove(combined.size() - 1);
        combined.addAll(second);
        combined.remove(combined.size() - 1);
        if (combined.size() < 12) {
            throw new IllegalStateException("Contour simplification removed too much detail");
        }
        return combined;
    }

    private static List<Point> simplifyOpen(List<Point> points, double tolerance) {
        if (points.size() <= 2) {
            return new ArrayList<>(points);
        }
        Point first = points.get(0);
        Point last = points.get(points.size() - 1);
        int splitIndex = -1;
        double greatestDistance = -1;
        for (int index = 1; index < points.size() - 1; index++) {
            double distance = perpendicularDistance(points.get(index), first, last);
            if (distance > greatestDistance) {
                greatestDistance = distance;
                splitIndex = index;
            }
        }
        if (greatestDistance <= tolerance) {
            List<Point> result = new ArrayList<>();
            result.add(first);
            result.add(last);
            return result;
        }
        List<Point> left = simplifyOpen(new ArrayList<>(points.subList(0, splitIndex + 1)), tolerance);
        List<Point> right = simplifyOpen(new ArrayList<>(points.subList(splitIndex, points.size())), tolerance);
        left.remove(left.size() - 1);
        left.addAll(right);
        return left;
    }

    private static double perpendicularDistance(Point point, Point lineStart, Point lineEnd) {
        double dx = lineEnd.x - lineStart.x;
        double dy = lineEnd.y - lineStart.y;
        if (dx == 0 && dy == 0) {
            return Math.sqrt(point.distanceSquared(lineStart));
        }
        double numerator = Math.abs(
                dy * point.x - dx * point.y + lineEnd.x * lineStart.y - lineEnd.y * lineStart.x);
        return numerator / Math.sqrt(dx * dx + dy * dy);
    }

    private static List<Point> scaleToViewport(
            List<Point> points,
            int width,
            int height,
            double safeScale) {
        List<Point> scaled = new ArrayList<>(points.size());
        for (Point point : points) {
            double rawX = (point.x + 0.5) * VIEWPORT / width;
            double rawY = (point.y + 0.5) * VIEWPORT / height;
            scaled.add(new Point(
                    VIEWPORT / 2 + (rawX - VIEWPORT / 2) * safeScale,
                    VIEWPORT / 2 + (rawY - VIEWPORT / 2) * safeScale));
        }
        return scaled;
    }

    private static String buildSmoothPathData(List<Point> points) {
        StringBuilder path = new StringBuilder();
        Point first = points.get(0);
        path.append("M").append(number(first.x)).append(',').append(number(first.y));
        int count = points.size();
        for (int index = 0; index < count; index++) {
            Point previous = points.get((index - 1 + count) % count);
            Point current = points.get(index);
            Point next = points.get((index + 1) % count);
            Point afterNext = points.get((index + 2) % count);
            // A slightly restrained Catmull-Rom conversion follows the traced contour
            // without introducing visible overshoot around the handset's inner bends.
            Point firstControl = new Point(
                    current.x + (next.x - previous.x) / 7.2,
                    current.y + (next.y - previous.y) / 7.2);
            Point secondControl = new Point(
                    next.x - (afterNext.x - current.x) / 7.2,
                    next.y - (afterNext.y - current.y) / 7.2);
            path.append(" C")
                    .append(number(firstControl.x)).append(',').append(number(firstControl.y)).append(' ')
                    .append(number(secondControl.x)).append(',').append(number(secondControl.y)).append(' ')
                    .append(number(next.x)).append(',').append(number(next.y));
        }
        path.append('Z');
        return path.toString();
    }

    private static String number(double value) {
        String formatted = String.format(Locale.ROOT, "%.3f", value);
        return formatted.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String phoneMarkSvg(String pathData) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" role="img" aria-label="Dot Dialer phone mark">
                  <path fill="#FFFFFF" d="%s"/>
                </svg>
                """.formatted(pathData);
    }

    private static String androidActionVector(String pathData, double rotation) {
        return """
                <?xml version="1.0" encoding="utf-8"?>
                <vector xmlns:android="http://schemas.android.com/apk/res/android"
                    android:width="24dp"
                    android:height="24dp"
                    android:viewportWidth="108"
                    android:viewportHeight="108">
                    <!-- Generated from the one approved Dot Dialer handset contour. -->
                    <group
                        android:pivotX="54"
                        android:pivotY="54"
                        android:rotation="%s"
                        android:scaleX="1.45"
                        android:scaleY="1.45">
                        <path
                            android:fillColor="#FF000000"
                            android:pathData="%s" />
                    </group>
                </vector>
                """.formatted(number(rotation), pathData);
    }

    private static String composeBrandIcons(String pathData) {
        return """
                package dev.goodwy.rphone.view.components

                import androidx.compose.ui.graphics.Color
                import androidx.compose.ui.graphics.SolidColor
                import androidx.compose.ui.graphics.vector.ImageVector
                import androidx.compose.ui.graphics.vector.PathParser
                import androidx.compose.ui.unit.dp

                // Generated by tools/branding/BrandAssetGenerator.java from the approved brand master.
                // Keep every phone action on this one contour; only the hang-up orientation differs.
                private const val DOT_DIALER_HANDSET_PATH = "%s"
                private const val DOT_DIALER_PLUS_PATH =
                    "M78,13 L86,13 L86,22 L95,22 L95,30 L86,30 L86,39 L78,39 L78,30 L69,30 L69,22 L78,22 Z"
                private const val DOT_DIALER_PAUSE_PATH =
                    "M75,15 L83,15 L83,37 L75,37 Z M88,15 L96,15 L96,37 L88,37 Z"
                private const val DOT_DIALER_DISABLED_PATH =
                    "M18,24 L24,18 L90,84 L84,90 Z"

                val DotDialerHandsetIcon: ImageVector by lazy {
                    dotDialerHandsetIcon(name = "DotDialerHandset", rotation = 0f)
                }

                val DotDialerHangupIcon: ImageVector by lazy {
                    dotDialerHandsetIcon(name = "DotDialerHangup", rotation = -45f)
                }

                val DotDialerAddCallIcon: ImageVector by lazy {
                    dotDialerHandsetIcon(
                        name = "DotDialerAddCall",
                        scale = 1.30f,
                        overlayPath = DOT_DIALER_PLUS_PATH,
                    )
                }

                val DotDialerPhonePausedIcon: ImageVector by lazy {
                    dotDialerHandsetIcon(
                        name = "DotDialerPhonePaused",
                        scale = 1.30f,
                        overlayPath = DOT_DIALER_PAUSE_PATH,
                    )
                }

                val DotDialerPhoneDisabledIcon: ImageVector by lazy {
                    dotDialerHandsetIcon(
                        name = "DotDialerPhoneDisabled",
                        scale = 1.30f,
                        overlayPath = DOT_DIALER_DISABLED_PATH,
                    )
                }

                private fun dotDialerHandsetIcon(
                    name: String,
                    rotation: Float = 0f,
                    scale: Float = 1.45f,
                    overlayPath: String? = null,
                ): ImageVector =
                    ImageVector.Builder(
                        name = name,
                        defaultWidth = 24.dp,
                        defaultHeight = 24.dp,
                        viewportWidth = 108f,
                        viewportHeight = 108f,
                    ).apply {
                        addGroup(
                            name = "brand-handset",
                            rotate = rotation,
                            pivotX = 54f,
                            pivotY = 54f,
                            scaleX = scale,
                            scaleY = scale,
                        )
                        addPath(
                            pathData = PathParser().parsePathString(DOT_DIALER_HANDSET_PATH).toNodes(),
                            fill = SolidColor(Color.Black),
                        )
                        clearGroup()
                        overlayPath?.let { path ->
                            addPath(
                                pathData = PathParser().parsePathString(path).toNodes(),
                                fill = SolidColor(Color.Black),
                            )
                        }
                    }.build()
                """.formatted(pathData);
    }

    private static String androidActionVectorWithStar(String pathData) {
        return """
                <?xml version="1.0" encoding="utf-8"?>
                <vector xmlns:android="http://schemas.android.com/apk/res/android"
                    android:width="24dp"
                    android:height="24dp"
                    android:viewportWidth="108"
                    android:viewportHeight="108">
                    <!-- Generated from the approved Dot Dialer handset plus a semantic star badge. -->
                    <group
                        android:pivotX="54"
                        android:pivotY="54"
                        android:scaleX="1.34"
                        android:scaleY="1.34">
                        <path
                            android:fillColor="#FF000000"
                            android:pathData="%s" />
                    </group>
                    <path
                        android:fillColor="#FF000000"
                        android:pathData="M82,12 L85.6,22.6 L97,22.8 L87.9,29.6 L91.2,40.5 L82,34.1 L72.8,40.5 L76.1,29.6 L67,22.8 L78.4,22.6 Z" />
                </vector>
                """.formatted(pathData);
    }

    private static String jadeMarkSvg(String pathData) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" role="img" aria-label="Dot Dialer mark">
                  <path fill="#00BB77" d="%s"/>
                </svg>
                """.formatted(pathData);
    }

    private static String appIconSvg(String pathData) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" role="img" aria-label="Dot Dialer app icon">
                  <rect width="108" height="108" rx="27" fill="#00BB77"/>
                  <path fill="#FFFFFF" d="%s"/>
                </svg>
                """.formatted(pathData);
    }

    private static String logoLockupSvg(String pathData, String wordmarkPath) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 540 120" role="img" aria-label="dot.dialer">
                  <g transform="translate(6 6)">
                    <path fill="#00BB77" d="%s"/>
                  </g>
                  <path fill="#10231C" d="%s"/>
                </svg>
                """.formatted(pathData, wordmarkPath);
    }

    private static Shape buildWordmarkOutline(Path root) throws Exception {
        Path fontFile = root.resolve("app/src/main/res/font/manrope_variable.ttf");
        Font font = Font.createFont(Font.TRUETYPE_FONT, fontFile.toFile())
                .deriveFont(Font.BOLD, 68f);
        FontRenderContext context = new FontRenderContext(new AffineTransform(), true, true);
        GlyphVector glyphs = font.createGlyphVector(context, "dot.dialer");
        Shape outline = glyphs.getOutline(132f, 82f);
        Area productionWordmark = new Area(outline);
        productionWordmark.add(new Area(new BasicStroke(
                1.4f,
                BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND).createStrokedShape(outline)));
        return productionWordmark;
    }

    private static String shapeToSvgPath(Shape shape) {
        PathIterator iterator = shape.getPathIterator(null);
        double[] coordinates = new double[6];
        StringBuilder path = new StringBuilder();
        while (!iterator.isDone()) {
            int type = iterator.currentSegment(coordinates);
            switch (type) {
                case PathIterator.SEG_MOVETO -> path.append("M")
                        .append(number(coordinates[0])).append(',').append(number(coordinates[1]));
                case PathIterator.SEG_LINETO -> path.append(" L")
                        .append(number(coordinates[0])).append(',').append(number(coordinates[1]));
                case PathIterator.SEG_QUADTO -> path.append(" Q")
                        .append(number(coordinates[0])).append(',').append(number(coordinates[1])).append(' ')
                        .append(number(coordinates[2])).append(',').append(number(coordinates[3]));
                case PathIterator.SEG_CUBICTO -> path.append(" C")
                        .append(number(coordinates[0])).append(',').append(number(coordinates[1])).append(' ')
                        .append(number(coordinates[2])).append(',').append(number(coordinates[3])).append(' ')
                        .append(number(coordinates[4])).append(',').append(number(coordinates[5]));
                case PathIterator.SEG_CLOSE -> path.append('Z');
                default -> throw new IllegalStateException("Unsupported wordmark path segment " + type);
            }
            iterator.next();
        }
        return path.toString();
    }

    private static String androidVector(String pathData) {
        return """
                <?xml version="1.0" encoding="utf-8"?>
                <vector xmlns:android="http://schemas.android.com/apk/res/android"
                    android:width="108dp"
                    android:height="108dp"
                    android:viewportWidth="108"
                    android:viewportHeight="108">
                    <!-- Generated from the approved Dot Dialer master; keep foreground and monochrome identical. -->
                    <path
                        android:fillColor="#FFFFFFFF"
                        android:pathData="%s" />
                </vector>
                """.formatted(pathData);
    }

    private static String androidAppIconVector(String pathData) {
        return """
                <?xml version="1.0" encoding="utf-8"?>
                <vector xmlns:android="http://schemas.android.com/apk/res/android"
                    android:width="108dp"
                    android:height="108dp"
                    android:viewportWidth="108"
                    android:viewportHeight="108">
                    <path
                        android:fillColor="#FF00BB77"
                        android:pathData="M27,0 H81 C95.912,0 108,12.088 108,27 V81 C108,95.912 95.912,108 81,108 H27 C12.088,108 0,95.912 0,81 V27 C0,12.088 12.088,0 27,0 Z" />
                    <path
                        android:fillColor="#FFFFFFFF"
                        android:pathData="%s" />
                </vector>
                """.formatted(pathData);
    }

    private static String androidNotificationVector(String pathData) {
        return """
                <?xml version="1.0" encoding="utf-8"?>
                <vector xmlns:android="http://schemas.android.com/apk/res/android"
                    android:width="24dp"
                    android:height="24dp"
                    android:viewportWidth="108"
                    android:viewportHeight="108">
                    <!-- Optical enlargement for Android's monochrome status-bar canvas. -->
                    <group
                        android:pivotX="54"
                        android:pivotY="54"
                        android:scaleX="1.55"
                        android:scaleY="1.55">
                        <path
                            android:fillColor="#FFFFFFFF"
                            android:pathData="%s" />
                    </group>
                </vector>
                """.formatted(pathData);
    }

    private static void renderLauncherAssets(Path root, List<Point> viewportPoints) throws IOException {
        String[] densities = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
        double[] exactScales = {1.0, 1.5, 2.0, 3.0, 4.0};
        for (int index = 0; index < densities.length; index++) {
            Path directory = root.resolve("app/src/main/res/mipmap-" + densities[index]);
            int launcherSize = (int) Math.round(48 * exactScales[index]);
            int adaptiveSize = (int) Math.round(108 * exactScales[index]);
            renderIcon(directory.resolve("ic_launcher.png"), viewportPoints, launcherSize, true, true);
            renderIcon(directory.resolve("ic_launcher_background.png"), viewportPoints, adaptiveSize, false, true);
            renderIcon(directory.resolve("ic_launcher_foreground.png"), viewportPoints, adaptiveSize, false, false);
            renderIcon(directory.resolve("ic_launcher_monochrome.png"), viewportPoints, adaptiveSize, false, false);
        }
    }

    private static void renderDocumentationPreview(Path target, List<Point> viewportPoints, int size)
            throws IOException {
        renderIcon(target, viewportPoints, size, true, true);
    }

    private static void renderLogoLockup(
            Path target,
            List<Point> viewportPoints,
            Shape wordmarkOutline) throws IOException {
        int scale = 2;
        BufferedImage canvas = new BufferedImage(540 * scale, 120 * scale, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        graphics.setColor(new Color(0xF7, 0xF5, 0xEF));
        graphics.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.scale(scale, scale);
        Path2D mark = buildPath(viewportPoints);
        graphics.setColor(JADE);
        graphics.fill(AffineTransform.getTranslateInstance(6, 6).createTransformedShape(mark));
        graphics.setColor(new Color(0x10, 0x23, 0x1C));
        graphics.fill(wordmarkOutline);
        graphics.dispose();
        Files.createDirectories(target.getParent());
        ImageIO.write(canvas, "png", target.toFile());
    }

    private static void renderIcon(
            Path target,
            List<Point> viewportPoints,
            int size,
            boolean roundedBackground,
            boolean drawBackground) throws IOException {
        int oversampling = 4;
        int canvasSize = size * oversampling;
        BufferedImage canvas = new BufferedImage(canvasSize, canvasSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.scale(oversampling, oversampling);
        if (drawBackground) {
            graphics.setColor(JADE);
            if (roundedBackground) {
                double radius = size * 0.25;
                graphics.fill(new RoundRectangle2D.Double(0, 0, size, size, radius * 2, radius * 2));
            } else {
                graphics.fillRect(0, 0, size, size);
            }
        }
        if (!drawBackground || roundedBackground) {
            Path2D mark = buildPath(viewportPoints);
            AffineTransform transform = AffineTransform.getScaleInstance(size / VIEWPORT, size / VIEWPORT);
            graphics.setColor(WHITE);
            graphics.fill(transform.createTransformedShape(mark));
        }
        graphics.dispose();

        BufferedImage output = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D downsample = output.createGraphics();
        downsample.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        downsample.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        downsample.drawImage(canvas, 0, 0, size, size, null);
        downsample.dispose();
        Files.createDirectories(target.getParent());
        ImageIO.write(output, "png", target.toFile());
    }

    private static Path2D buildPath(List<Point> points) {
        Path2D path = new Path2D.Double();
        Point first = points.get(0);
        path.moveTo(first.x, first.y);
        int count = points.size();
        for (int index = 0; index < count; index++) {
            Point previous = points.get((index - 1 + count) % count);
            Point current = points.get(index);
            Point next = points.get((index + 1) % count);
            Point afterNext = points.get((index + 2) % count);
            path.curveTo(
                    current.x + (next.x - previous.x) / 7.2,
                    current.y + (next.y - previous.y) / 7.2,
                    next.x - (afterNext.x - current.x) / 7.2,
                    next.y - (afterNext.y - current.y) / 7.2,
                    next.x,
                    next.y);
        }
        path.closePath();
        return path;
    }

    private static void validateSafeZone(List<Point> contour) {
        Bounds bounds = bounds(contour);
        if (bounds.minX < 18 || bounds.minY < 18 || bounds.maxX > 90 || bounds.maxY > 90) {
            throw new IllegalStateException("Mark exceeds the adaptive icon's generous 72x72 guard box");
        }
        double maxRadius = maxRadius(contour);
        if (maxRadius > SAFE_RADIUS) {
            throw new IllegalStateException(String.format(
                    Locale.ROOT,
                    "Mark exceeds Android's 66dp safe circle: %.2f > %.2f",
                    maxRadius,
                    SAFE_RADIUS));
        }
    }

    private static double maxRadius(List<Point> points) {
        double maximum = 0;
        Point center = new Point(VIEWPORT / 2, VIEWPORT / 2);
        for (Point point : points) {
            maximum = Math.max(maximum, Math.sqrt(point.distanceSquared(center)));
        }
        return maximum;
    }

    private static List<Point> scaleAroundViewportCenter(List<Point> points, double scale) {
        Point center = new Point(VIEWPORT / 2, VIEWPORT / 2);
        List<Point> scaled = new ArrayList<>(points.size());
        for (Point point : points) {
            scaled.add(new Point(
                    center.x + (point.x - center.x) * scale,
                    center.y + (point.y - center.y) * scale));
        }
        return scaled;
    }

    private record Bounds(double minX, double minY, double maxX, double maxY) {}

    private static Bounds bounds(List<Point> points) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (Point point : points) {
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
        }
        return new Bounds(minX, minY, maxX, maxY);
    }

    private static void writeText(Path target, String content) throws IOException {
        Files.createDirectories(target.getParent());
        Files.writeString(target, content, StandardCharsets.UTF_8);
    }
}
