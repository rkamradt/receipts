package com.rkamradt.receipts.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Accepts JPEG, PNG, WebP image bytes; fixes EXIF rotation; downscales so the
 * long edge is ≤ 1568 px; returns JPEG bytes ready for the Anthropic API.
 *
 * HEIC images are not directly supported — the frontend should prompt the user
 * to convert to JPEG before upload (iOS/Android share sheets do this automatically
 * when the app requests "photo" rather than "file").
 */
@Service
public class ImageProcessingService {

    private static final Logger log = LoggerFactory.getLogger(ImageProcessingService.class);
    private static final int MAX_LONG_EDGE = 1568;

    public byte[] process(byte[] rawBytes, String contentType) throws IOException {
        if (rawBytes == null || rawBytes.length == 0) {
            throw new IllegalArgumentException("Empty image");
        }

        // 1. Read EXIF orientation before ImageIO strips it
        int orientation = readExifOrientation(rawBytes);

        // 2. Decode image
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(rawBytes));
        if (img == null) {
            throw new IOException("Cannot decode image (unsupported format or corrupt data)");
        }

        // 3. Apply EXIF rotation
        img = applyOrientation(img, orientation);

        // 4. Downscale if needed
        int maxDim = Math.max(img.getWidth(), img.getHeight());
        if (maxDim > MAX_LONG_EDGE) {
            double scale = (double) MAX_LONG_EDGE / maxDim;
            img = Thumbnails.of(img)
                .scale(scale)
                .asBufferedImage();
        }

        // 5. Encode as JPEG
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thumbnails.of(img)
            .scale(1.0)
            .outputFormat("JPEG")
            .outputQuality(0.85)
            .toOutputStream(out);

        log.info("Processed image: {} bytes in → {} bytes out", rawBytes.length, out.size());
        return out.toByteArray();
    }

    private int readExifOrientation(byte[] bytes) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(bytes));
            ExifIFD0Directory dir = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (dir != null && dir.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                return dir.getInt(ExifIFD0Directory.TAG_ORIENTATION);
            }
        } catch (Exception e) {
            log.debug("Could not read EXIF orientation: {}", e.getMessage());
        }
        return 1; // normal
    }

    private BufferedImage applyOrientation(BufferedImage img, int orientation) {
        if (orientation <= 1) return img;

        int w = img.getWidth();
        int h = img.getHeight();
        AffineTransform t = new AffineTransform();

        switch (orientation) {
            case 2 -> { t.scale(-1, 1); t.translate(-w, 0); }
            case 3 -> { t.translate(w, h); t.rotate(Math.PI); }
            case 4 -> { t.scale(1, -1); t.translate(0, -h); }
            case 5 -> { t.rotate(-Math.PI / 2); t.scale(-1, 1); }
            case 6 -> { t.translate(h, 0); t.rotate(Math.PI / 2); }
            case 7 -> { t.scale(-1, 1); t.translate(-h, 0); t.translate(0, w); t.rotate(3 * Math.PI / 2); }
            case 8 -> { t.translate(0, w); t.rotate(-Math.PI / 2); }
            default -> { return img; }
        }

        boolean swapDims = (orientation >= 5 && orientation <= 8);
        int newW = swapDims ? h : w;
        int newH = swapDims ? w : h;
        BufferedImage result = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = result.createGraphics();
        g.setTransform(t);
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return result;
    }
}
