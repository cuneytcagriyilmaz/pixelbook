package com.pixelbook.stego;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Tuvali sabit RGB uzayında tutar. LSB ancak piksel değerleri kayıpsız kaldığında geri okunur.
 * PNG bu yüzden 24 bit renk olarak yazılır. JPEG bu sınıftan çıkmaz.
 */
public final class RgbImages {

    private RgbImages() {
    }

    /**
     * Tuvalin taşıyabileceği gömülü bayt sayısını hesaplar.
     * Gelen: genişlik 200, yükseklik 300.
     * İçeride: 200 * 300 = 60000 piksel, her pikselde 3 kanal, 180000 bit.
     * Dışarı: 180000 / 8 = 22500 bayt.
     */
    public static int capacityBytes(int width, int height) {
        long bits = (long) width * height * 3L;
        return (int) (bits / 8);
    }

    /**
     * Kaynak resmi alfa kanalı olmayan RGB tuvale kopyalar.
     * Opak örnek: gelen piksel 0xFF336699. Alfa 255 olduğu için kırmızı 0x33, yeşil 0x66, mavi 0x99 aynı kalır.
     * Dışarı: 0xFF336699.
     * Yarı saydam örnek: gelen 0x80000000, yani alfa 128 ve siyah. Beyaz zemine oturtulur.
     * Kırmızı = (0 * 128 + 255 * 127) / 255 = 127. Yeşil ve mavi de 127 olur.
     * Dışarı: 0xFF7F7F7F.
     */
    public static BufferedImage copyAsRgb(BufferedImage source) {
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                rgb.setRGB(x, y, blendOnWhite(source.getRGB(x, y)));
            }
        }
        return rgb;
    }

    /**
     * Tek pikseli beyaz zemine yapıştırır.
     * Gelen: 0xFF336699.
     * İçeride: alfa 255, renk 0x336699. Beyazın katkısı 0'dır.
     * Dışarı: 0xFF336699.
     */
    static int blendOnWhite(int argb) {
        int alpha = (argb >>> 24) & 0xFF;
        int red = (argb >> 16) & 0xFF;
        int green = (argb >> 8) & 0xFF;
        int blue = argb & 0xFF;
        red = (red * alpha + 255 * (255 - alpha)) / 255;
        green = (green * alpha + 255 * (255 - alpha)) / 255;
        blue = (blue * alpha + 255 * (255 - alpha)) / 255;
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    /**
     * Çerçeve sığmazsa tuvalin altına beyaz satır ekler. Sığıyorsa aynı tuvali geri verir.
     * Gelen: 2x2 resim ve 43 baytlık çerçeve.
     * İçeride: 2x2 tuval 12 bit, yani 1 bayt taşır. 43 bayt = 344 bit. Bir satır 6 bit taşır.
     * Yeni yükseklik = ceil(344 / 6) = 58. Üstteki 2 satır eski piksellerdir, alttaki 56 satır 0xFFFFFF olur.
     * Dışarı: genişlik 2, yükseklik 58 olan RGB tuval.
     */
    public static BufferedImage expandIfNeeded(BufferedImage source, int neededBytes) {
        int width = source.getWidth();
        int height = source.getHeight();
        if (capacityBytes(width, height) >= neededBytes) {
            return source;
        }
        long neededBits = (long) neededBytes * 8L;
        long rowBits = (long) width * 3L;
        int newHeight = (int) ((neededBits + rowBits - 1) / rowBits);
        BufferedImage expanded = new BufferedImage(width, newHeight, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < newHeight; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = y < height ? source.getRGB(x, y) : 0xFFFFFFFF;
                expanded.setRGB(x, y, pixel | 0xFF000000);
            }
        }
        return expanded;
    }

    /**
     * Dosya baytlarını RGB tuvale çevirir.
     * Gelen: PNG bayt dizisi, ilk sekiz bayt PNG imzası 89 50 4E 47 0D 0A 1A 0A.
     * İçeride: ImageIO pikselleri çözer, copyAsRgb her pikseli opak RGB yapar.
     * Dışarı: TYPE_INT_RGB tuval. Dosya resim değilse "Dosya resim olarak açılamadı." fırlatılır.
     */
    public static BufferedImage read(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new StegoException("Fotoğraf boş.");
        }
        try {
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(bytes));
            if (decoded == null) {
                throw new StegoException("Dosya resim olarak açılamadı.");
            }
            return copyAsRgb(decoded);
        } catch (IOException ex) {
            throw new StegoException("Dosya resim olarak açılamadı.", ex);
        }
    }

    /**
     * RGB tuvali PNG baytlarına yazar.
     * Gelen: TYPE_INT_RGB tuval, örnek piksel 0xFF000100.
     * İçeride: kayıpsız PNG. Renk tipi gerçek renk olur, palete indirgenmez.
     * Dışarı: bayt dizisi. Aynı dosya tekrar okununca 0xFF000100 pikseli yerinde kalır.
     */
    public static byte[] toPng(BufferedImage rgb) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            boolean written = ImageIO.write(rgb, "png", out);
            if (!written) {
                throw new StegoException("PNG yazıcı bulunamadı.");
            }
            return out.toByteArray();
        } catch (IOException ex) {
            throw new StegoException("PNG yazılamadı.", ex);
        }
    }

    /**
     * Kamera dosyası olmadan denenecek düz bir vesikalık çizer. Bu metot JSON gömmez.
     * Gelen: "Ahmet Yılmaz".
     * İçeride: 320x400 lacivert zemin, ten rengi oval, altta açık kart ve bu yazı.
     * Dışarı: PNG baytları. Yazı mürekkep olarak görünür; uzunluk başlığı henüz yoktur.
     */
    public static byte[] samplePortrait(String caption) {
        BufferedImage image = new BufferedImage(320, 400, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0x1C3A5F));
        g.fillRect(0, 0, 320, 400);
        g.setColor(new Color(0xD7C4A3));
        g.fillOval(95, 60, 130, 160);
        g.setColor(new Color(0xF4EFE4));
        g.fillRect(40, 250, 240, 110);
        g.setColor(new Color(0x1B1915));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        g.drawString(caption, 52, 312);
        g.dispose();
        return toPng(image);
    }
}
