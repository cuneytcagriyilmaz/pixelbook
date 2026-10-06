package com.pixelbook.stego;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * JSON baytlarını uzunluk başlığı ve HMAC ile tek çerçeveye kapatır, geri açarken mührü kontrol eder.
 */
public final class PayloadFrame {

    public static final int LENGTH_BYTES = 4;
    public static final int HMAC_BYTES = 32;

    private final byte[] key;

    /**
     * HMAC sırrını kopyalayarak saklar.
     * Gelen: "pixelbook-demo-hmac-anahtari" baytları.
     * İçeride: dizinin kopyası tutulur. Çağıran kendi dizisini sonradan değiştirse sır bozulmaz.
     * Dışarı: bu nesne. Anahtar boşsa "HMAC anahtarı boş." fırlatılır.
     */
    public PayloadFrame(byte[] key) {
        if (key == null || key.length == 0) {
            throw new StegoException("HMAC anahtarı boş.");
        }
        this.key = key.clone();
    }

    /**
     * JSON baytlarını mühürlü çerçeveye çevirir.
     * Gelen: {"a":1} baytları, 7 bayt, onaltılık 7B 22 61 22 3A 31 7D.
     * İçeride: uzunluk 00 00 00 07, imza bu 11 baytın HMAC-SHA256 sonucudur ve 32 bayttır.
     * Dışarı: 4 + 7 + 32 = 43 bayt. İlk dört bayt 00 00 00 07, sonraki 7 bayt JSON'un kendisidir.
     */
    public byte[] seal(byte[] json) {
        byte[] length = writeLength(json.length);
        byte[] signed = concat(length, json);
        byte[] mac = hmac(signed);
        return concat(signed, mac);
    }

    /**
     * Çerçeveyi açar ve mührü yeniden hesaplayarak karşılaştırır.
     * Gelen: seal çıktısı, 43 bayt, başı 00 00 00 07.
     * İçeride: uzunluk 7 okunur, JSON 7 bayt kesilir, kalan 32 bayt verilen imzadır.
     * Beklenen imza, baştaki 11 bayttan tekrar üretilir.
     * Dışarı: imza aynıysa signatureValid true ve json {"a":1} baytları.
     * JSON'un ilk baytı 7B iken 7A yapılırsa signatureValid false olur. JSON baytları yine döner.
     */
    public Opened open(byte[] frame) {
        if (frame.length < LENGTH_BYTES + HMAC_BYTES) {
            throw new StegoException("Çerçeve, uzunluk ve imza için kısa.");
        }
        int jsonLength = readLength(frame);
        int expected = LENGTH_BYTES + jsonLength + HMAC_BYTES;
        if (jsonLength < 2 || expected != frame.length) {
            throw new StegoException("Uzunluk başlığı çerçeve boyuyla uyuşmuyor.");
        }
        byte[] json = Arrays.copyOfRange(frame, LENGTH_BYTES, LENGTH_BYTES + jsonLength);
        byte[] given = Arrays.copyOfRange(frame, LENGTH_BYTES + jsonLength, expected);
        byte[] calculated = hmac(Arrays.copyOfRange(frame, 0, LENGTH_BYTES + jsonLength));
        boolean valid = MessageDigest.isEqual(calculated, given);
        return new Opened(json, valid);
    }

    /**
     * Bayt sayısını 4 baytlık big-endian başlığa çevirir.
     * Gelen: 13.
     * İçeride: 13 = 0x0000000D.
     * Dışarı: [0, 0, 0, 13]. Gelen 256 ise dışarı [0, 0, 1, 0].
     */
    public static byte[] writeLength(int value) {
        return new byte[]{
                (byte) (value >>> 24),
                (byte) (value >>> 16),
                (byte) (value >>> 8),
                (byte) value
        };
    }

    /**
     * 4 baytlık big-endian başlığı sayıya çevirir.
     * Gelen: [0, 0, 0, 13].
     * İçeride: (0 << 24) | (0 << 16) | (0 << 8) | 13.
     * Dışarı: 13. Gelen [0, 0, 1, 0] ise dışarı 256.
     */
    public static int readLength(byte[] four) {
        return ((four[0] & 0xFF) << 24)
                | ((four[1] & 0xFF) << 16)
                | ((four[2] & 0xFF) << 8)
                | (four[3] & 0xFF);
    }

    /**
     * İki bayt dizisini uç uca ekler.
     * Gelen: [0, 0, 0, 7] ve [123, 34].
     * İçeride: yeni dizinin başına birinci, 4. indeksten itibaren ikinci kopyalanır.
     * Dışarı: [0, 0, 0, 7, 123, 34].
     */
    static byte[] concat(byte[] left, byte[] right) {
        byte[] out = new byte[left.length + right.length];
        System.arraycopy(left, 0, out, 0, left.length);
        System.arraycopy(right, 0, out, left.length, right.length);
        return out;
    }

    /**
     * HMAC-SHA256 üretir.
     * Gelen: 11 bayt (00 00 00 07 + {"a":1}).
     * İçeride: HmacSHA256, sır olarak kurucu anahtar. Her çağrıda yeni Mac kurulur.
     * Dışarı: 32 bayt. Aynı giriş ve aynı anahtar her seferinde aynı 32 baytı verir.
     */
    private byte[] hmac(byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException ex) {
            throw new StegoException("HMAC hesaplanamadı.", ex);
        }
    }

    /**
     * Açılmış çerçeve.
     * Gelen JSON {"a":1} baytları ve imza sonucu true.
     * Dışarı: json() aynı baytlar, signatureValid() true. İmza bozulmuşsa json durur, signatureValid false olur.
     */
    public record Opened(byte[] json, boolean signatureValid) {
    }
}
