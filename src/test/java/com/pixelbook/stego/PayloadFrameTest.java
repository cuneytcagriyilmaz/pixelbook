package com.pixelbook.stego;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayloadFrameTest {

    /**
     * Küçük JSON'un çerçeve boyunu kilitler.
     * Gelen: {"a":1}, 7 bayt.
     * Dışarı: 43 bayt, başı 00 00 00 07, imza geçerli, JSON aynı metin.
     */
    @Test
    void sealsSevenJsonBytesIntoFortyThree() {
        byte[] json = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);
        PayloadFrame frame = new PayloadFrame("anahtar".getBytes(StandardCharsets.UTF_8));
        byte[] sealed = frame.seal(json);

        assertEquals(43, sealed.length);
        assertArrayEquals(new byte[]{0, 0, 0, 7}, Arrays.copyOf(sealed, 4));
        PayloadFrame.Opened opened = frame.open(sealed);
        assertTrue(opened.signatureValid());
        assertEquals("{\"a\":1}", new String(opened.json(), StandardCharsets.UTF_8));
    }

    /**
     * JSON'un ilk baytı değişince mühür düşer.
     * Gelen: '{' = 0x7B.
     * İçeride ilk JSON baytı 0x7A olur.
     * Dışarı: signatureValid false. Uzunluk başlığı yerinde kalır.
     */
    @Test
    void rejectsAFlippedJsonByte() {
        PayloadFrame frame = new PayloadFrame("anahtar".getBytes(StandardCharsets.UTF_8));
        byte[] sealed = frame.seal("{\"a\":1}".getBytes(StandardCharsets.UTF_8));
        sealed[4] = (byte) (sealed[4] ^ 1);

        assertFalse(frame.open(sealed).signatureValid());
    }

    /**
     * Uzunluk başlığının iki yönünü kilitler.
     * Gelen 13, dışarı [0, 0, 0, 13], tekrar okununca 13.
     * Gelen 256, dışarı [0, 0, 1, 0], tekrar okununca 256.
     */
    @Test
    void writesLengthBigEndian() {
        assertArrayEquals(new byte[]{0, 0, 0, 13}, PayloadFrame.writeLength(13));
        assertEquals(13, PayloadFrame.readLength(new byte[]{0, 0, 0, 13}));
        assertArrayEquals(new byte[]{0, 0, 1, 0}, PayloadFrame.writeLength(256));
        assertEquals(256, PayloadFrame.readLength(new byte[]{0, 0, 1, 0}));
    }
}
