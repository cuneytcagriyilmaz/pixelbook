package com.pixelbook.stego;

import java.awt.image.BufferedImage;

/**
 * Çerçeve baytlarını piksellerin son bitine yazar ve aynı sırayla geri okur.
 * Kanal sırası kırmızı, yeşil, mavi. Baytın yüksek biti önce gider.
 */
public final class LsbChannel {

    /**
     * Gelen çerçeveyi tuvalin başına gömer.
     * Gelen bayt 'A' = 0x41 = 01000001 ve siyah tuval 0xFF000000.
     * İçeride ilk pikselin kanallarına 0, 1, 0 yazılır. Yeşilin son biti 1 olunca piksel 0xFF000100 olur.
     * Dışarı: aynı tuval, extract ile okununca tekrar 'A'.
     */
    public void embed(BufferedImage image, byte[] frame) {
        requireRgb(image);
        int capacityBits = image.getWidth() * image.getHeight() * 3;
        int neededBits = frame.length * 8;
        if (neededBits > capacityBits) {
            throw new StegoException("Çerçeve tuvalden büyük.");
        }
        int bitIndex = 0;
        for (byte value : frame) {
            int unsigned = value & 0xFF;
            for (int shift = 7; shift >= 0; shift--) {
                writeBit(image, bitIndex, (unsigned >> shift) & 1);
                bitIndex++;
            }
        }
    }

    /**
     * Tuvalden ardışık baytları söker.
     * Gelen: byteOffset 0, byteLength 3, gömülü metin "ALI".
     * İçeride: 0. baytın 8 biti, sonra 1. baytın 8 biti, sonra 2. baytın 8 biti toplanır.
     * Dışarı: baytlar 0x41 0x4C 0x49, yani "ALI".
     */
    public byte[] extract(BufferedImage image, int byteOffset, int byteLength) {
        requireRgb(image);
        byte[] out = new byte[byteLength];
        for (int i = 0; i < byteLength; i++) {
            int value = 0;
            for (int shift = 7; shift >= 0; shift--) {
                int bit = readBit(image, (byteOffset + i) * 8 + (7 - shift));
                value = (value << 1) | bit;
            }
            out[i] = (byte) value;
        }
        return out;
    }

    /**
     * Tek bir kanalın son bitini yazar.
     * Gelen: bitIndex 1, bit 1, piksel 0xFF000000, tuval genişliği 4.
     * İçeride: 1 / 3 = 0. piksel, kanal sırası 1, yani yeşil. Yeşil baytın son biti 1 yapılır.
     * Dışarı: piksel (0,0) değeri 0xFF000100.
     */
    public void writeBit(BufferedImage image, int bitIndex, int bit) {
        requireRgb(image);
        Located spot = locate(image, bitIndex);
        int shift = 16 - spot.channel * 8;
        int rgb = image.getRGB(spot.x, spot.y);
        int updated = (rgb & ~(1 << shift)) | ((bit & 1) << shift) | 0xFF000000;
        image.setRGB(spot.x, spot.y, updated);
    }

    /**
     * Tek bir kanalın son bitini okur.
     * Gelen: bitIndex 1, piksel (0,0) = 0xFF000100.
     * İçeride: yeşil bayt 0x01, son bit 1.
     * Dışarı: 1.
     */
    public int readBit(BufferedImage image, int bitIndex) {
        requireRgb(image);
        Located spot = locate(image, bitIndex);
        int shift = 16 - spot.channel * 8;
        return (image.getRGB(spot.x, spot.y) >> shift) & 1;
    }

    /**
     * Seçilen son biti tersine çevirir. HMAC yeniden hesaplanmaz.
     * Gelen: bit 0.
     * İçeride: 0 xor 1 = 1.
     * Dışarı: kanala yazılan bit 1. Gelen bit 1 ise dışarı 0 olur.
     */
    public void flipBit(BufferedImage image, int bitIndex) {
        int current = readBit(image, bitIndex);
        writeBit(image, bitIndex, current ^ 1);
    }

    /**
     * Bit indeksini piksel koordinatına çevirir.
     * Gelen: bitIndex 1, genişlik 4.
     * İçeride: piksel sırası 1 / 3 = 0, kanal 1 % 3 = 1, x = 0 % 4 = 0, y = 0 / 4 = 0.
     * Dışarı: x 0, y 0, kanal 1 (yeşil).
     */
    private static Located locate(BufferedImage image, int bitIndex) {
        int width = image.getWidth();
        int pixelIndex = bitIndex / 3;
        int x = pixelIndex % width;
        int y = pixelIndex / width;
        int channel = bitIndex % 3;
        if (y < 0 || y >= image.getHeight()) {
            throw new StegoException("Bit indeksi tuvalin dışında.");
        }
        return new Located(x, y, channel);
    }

    /**
     * LSB yalnızca alfa'sız RGB tuvalde çalışır.
     * Gelen tip TYPE_INT_RGB ise metot sessizce döner.
     * Gelen tip bundan farklıysa "LSB yalnızca TYPE_INT_RGB tuvaline yazılır." fırlatılır.
     */
    private static void requireRgb(BufferedImage image) {
        if (image.getType() != BufferedImage.TYPE_INT_RGB) {
            throw new StegoException("LSB yalnızca TYPE_INT_RGB tuvaline yazılır.");
        }
    }

    /**
     * Bir bitin oturduğu yer.
     * Gelen: x 0, y 0, kanal 1.
     * Dışarı: aynı üç sayı. Kanal 0 kırmızı, 1 yeşil, 2 mavidir.
     */
    private record Located(int x, int y, int channel) {
    }
}
