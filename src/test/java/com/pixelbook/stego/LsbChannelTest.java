package com.pixelbook.stego;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LsbChannelTest {

    /**
     * 'A' baytının ilk yeşil bite oturduğunu kilitler.
     * Gelen: 0x41 = 01000001, siyah tuval.
     * Dışarı: piksel (0,0) = 0xFF000100 ve okunan bayt yine 0x41.
     */
    @Test
    void embedsLetterAIntoTheGreenBit() {
        BufferedImage image = black(8, 8);
        LsbChannel channel = new LsbChannel();
        channel.embed(image, new byte[]{0x41});

        assertEquals(0xFF000100, image.getRGB(0, 0));
        assertArrayEquals(new byte[]{0x41}, channel.extract(image, 0, 1));
    }

    /**
     * Üç harf gömülüp aynı sırayla okunur.
     * Gelen: "ALI" = 41 4C 49.
     * Dışarı: "ALI".
     */
    @Test
    void roundTripsAli() {
        BufferedImage image = black(8, 8);
        LsbChannel channel = new LsbChannel();
        channel.embed(image, "ALI".getBytes());

        assertEquals("ALI", new String(channel.extract(image, 0, 3)));
    }

    /**
     * Rakam baytının son biti çevrilince komşu rakam olur.
     * Gelen: '0' = 0x30.
     * İçeride bit indeksi 7, yani baytın en sağ biti.
     * Dışarı: '1' = 0x31.
     */
    @Test
    void flipsTheLastBitOfZeroIntoOne() {
        BufferedImage image = black(8, 8);
        LsbChannel channel = new LsbChannel();
        channel.embed(image, new byte[]{0x30});
        channel.flipBit(image, 7);

        assertArrayEquals(new byte[]{0x31}, channel.extract(image, 0, 1));
    }

    private static BufferedImage black(int width, int height) {
        return new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    }
}
