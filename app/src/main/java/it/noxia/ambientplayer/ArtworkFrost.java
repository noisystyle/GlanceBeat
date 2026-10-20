package it.noxia.ambientplayer;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

/** Small, cached CPU blur. Prepared once per artwork, never on each animation frame. */
final class ArtworkFrost {
    static Bitmap create(Bitmap artwork) {
        final int size = 64;
        Bitmap readable = artwork.getConfig() == Bitmap.Config.HARDWARE
            ? artwork.copy(Bitmap.Config.ARGB_8888, false) : artwork;
        if (readable == null) return null;
        Bitmap small = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        new Canvas(small).drawBitmap(readable, null, new Rect(0, 0, size, size),
            new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
        if (readable != artwork) readable.recycle();
        int[] pixels = new int[size * size];
        int[] buffer = new int[pixels.length];
        small.getPixels(pixels, 0, size, 0, 0, size, size);
        for (int pass = 0; pass < 3; pass++) {
            blur(pixels, buffer, size, true);
            blur(buffer, pixels, size, false);
        }
        for (int i = 0; i < pixels.length; i++) {
            int r = (pixels[i] >> 16) & 255, g = (pixels[i] >> 8) & 255, b = pixels[i] & 255;
            int grey = (r * 77 + g * 150 + b * 29) >> 8;
            // Muted artwork under a satin veil, with enough contrast for light text.
            r = ((r * 3 + grey) / 4) * 38 / 100 + 14;
            g = ((g * 3 + grey) / 4) * 38 / 100 + 14;
            b = ((b * 3 + grey) / 4) * 38 / 100 + 14;
            pixels[i] = 0xff000000 | (r << 16) | (g << 8) | b;
        }
        small.setPixels(pixels, 0, size, 0, 0, size, size);
        return small;
    }
    private static void blur(int[] source, int[] target, int size, boolean horizontal) {
        final int radius = 8, count = radius * 2 + 1;
        for (int line = 0; line < size; line++) {
            int red = 0, green = 0, blue = 0;
            for (int offset = -radius; offset <= radius; offset++) {
                int p = pixel(source, size, line, Math.max(0, offset), horizontal);
                red += (p >> 16) & 255; green += (p >> 8) & 255; blue += p & 255;
            }
            for (int pos = 0; pos < size; pos++) {
                int index = horizontal ? line * size + pos : pos * size + line;
                target[index] = 0xff000000 | ((red / count) << 16) | ((green / count) << 8) | (blue / count);
                int left = pixel(source, size, line, Math.max(0, pos - radius), horizontal);
                int right = pixel(source, size, line, Math.min(size - 1, pos + radius + 1), horizontal);
                red += ((right >> 16) & 255) - ((left >> 16) & 255);
                green += ((right >> 8) & 255) - ((left >> 8) & 255);
                blue += (right & 255) - (left & 255);
            }
        }
    }
    private static int pixel(int[] pixels, int size, int line, int pos, boolean horizontal) {
        return pixels[horizontal ? line * size + pos : pos * size + line];
    }
    private ArtworkFrost() { }
}
