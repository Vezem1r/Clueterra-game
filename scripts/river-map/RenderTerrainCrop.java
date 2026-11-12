import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipInputStream;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

public class RenderTerrainCrop {
    public static void main(String[] args) throws Exception {
        if (args.length == 3 && "--extract".equals(args[0])) {
            extractTiff(Path.of(args[1]), Path.of(args[2]));
            return;
        }
        if (args.length != 6) {
            throw new IllegalArgumentException("Expected TIFF, JPEG, minLon, maxLon, minLat, maxLat");
        }
        render(new File(args[0]), new File(args[1]), Double.parseDouble(args[2]),
                Double.parseDouble(args[3]), Double.parseDouble(args[4]), Double.parseDouble(args[5]));
    }

    private static void extractTiff(Path archive, Path target) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (entry.getName().endsWith("NE1_LR_LC_SR.tif")) {
                    Files.createDirectories(target.getParent());
                    Path temporary = target.resolveSibling(target.getFileName() + ".part");
                    try (FileOutputStream output = new FileOutputStream(temporary.toFile())) {
                        zip.transferTo(output);
                    }
                    Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    return;
                }
            }
        }
        throw new IllegalStateException("Natural Earth raster archive has no NE1_LR_LC_SR.tif");
    }

    private static void render(File source, File target, double minLon, double maxLon,
            double minLat, double maxLat) throws Exception {
        try (ImageInputStream input = ImageIO.createImageInputStream(source)) {
            ImageReader reader = ImageIO.getImageReaders(input).next();
            try {
                reader.setInput(input);
                int sourceWidth = reader.getWidth(0);
                int sourceHeight = reader.getHeight(0);
                double pixelsPerDegree = sourceWidth / 360.0;
                if (sourceWidth != 16200 || sourceHeight != 8100) {
                    throw new IllegalStateException("Unexpected Natural Earth raster dimensions");
                }
                int left = Math.max(0, (int) Math.floor((minLon + 180) * pixelsPerDegree));
                int top = Math.max(0, (int) Math.floor((90 - maxLat) * pixelsPerDegree));
                int right = Math.min(sourceWidth, (int) Math.ceil((maxLon + 180) * pixelsPerDegree));
                int bottom = Math.min(sourceHeight, (int) Math.ceil((90 - minLat) * pixelsPerDegree));
                if (right <= left || bottom <= top) {
                    throw new IllegalArgumentException("River map viewport is outside the raster");
                }
                ImageReadParam params = reader.getDefaultReadParam();
                params.setSourceRegion(new java.awt.Rectangle(left, top, right - left, bottom - top));
                BufferedImage crop = reader.read(0, params);
                BufferedImage output = new BufferedImage(900, 600, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = output.createGraphics();
                try {
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    graphics.drawImage(crop, 0, 0, 900, 600, null);
                } finally {
                    graphics.dispose();
                }
                ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
                try (ImageOutputStream imageOutput = ImageIO.createImageOutputStream(target)) {
                    writer.setOutput(imageOutput);
                    ImageWriteParam writeParams = writer.getDefaultWriteParam();
                    writeParams.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    writeParams.setCompressionQuality(0.86f);
                    writer.write(null, new IIOImage(output, null, null), writeParams);
                } finally {
                    writer.dispose();
                }
            } finally {
                reader.dispose();
            }
        }
    }
}
