package com.pixelbook.stego;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class RgbImagesTest {

    /**
     * Opak pikselin rengini korur, yarı saydam siyahi griye çevirir.
     * Gelen 0xFF336699, dışarı 0xFF336699.
     * Gelen 0x80000000, dışarı 0xFF7F7F7F.
     */
    @Test
    void blendsAlphaOntoWhite() {
        assertEquals(0xFF336699, RgbImages.blendOnWhite(0xFF336699));
        assertEquals(0xFF7F7F7F, RgbImages.blendOnWhite(0x80000000));
    }

    /**
     * 200x300 vesikalığın kapasitesini kilitler.
     * İçeride 180000 bit. Dışarı 22500 bayt.
     */
    @Test
    void countsCapacity() {
        assertEquals(22500, RgbImages.capacityBytes(200, 300));
    }

    /**
     * 2x2 tuvali 43 bayt için 58 satıra uzatır.
     * Üst piksel eski renkte kalır, en alt piksel beyazdır.
     */
    @Test
    void expandsATinyCanvas() {
        BufferedImage source = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        source.setRGB(0, 0, 0xFF112233);
        BufferedImage expanded = RgbImages.expandIfNeeded(source, 43);

        assertEquals(2, expanded.getWidth());
        assertEquals(58, expanded.getHeight());
        assertEquals(0xFF112233, expanded.getRGB(0, 0));
        assertEquals(0xFFFFFFFF, expanded.getRGB(0, 57));
    }

    /**
     * Sığan çerçeve yeni tuval açmaz.
     * Gelen 2x2 ve 1 bayt. Kapasite tam 1 bayttır.
     * Dışarı: aynı nesne.
     */
    @Test
    void keepsTheCanvasWhenTheFrameFits() {
        BufferedImage source = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        assertSame(source, RgbImages.expandIfNeeded(source, 1));
    }
}
