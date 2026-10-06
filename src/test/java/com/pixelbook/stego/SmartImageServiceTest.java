package com.pixelbook.stego;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixelbook.Messages;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmartImageServiceTest {

    private final SmartImageService service = new SmartImageService(new ObjectMapper(), "test-anahtar");

    /**
     * Kimlik JSON'unun biçimini ve PNG gidiş-dönüşünü kilitler.
     * Gelen: Ahmet Yılmaz, 2026001, Bilgisayar Mühendisliği.
     * Dışarı JSON dersler [] olur. ı ve ü yüzünden bayt sayısı karakter sayısından büyüktür.
     * Aynı PNG tekrar okununca ad aynıdır.
     */
    @Test
    void embedsIdentityAndReadsItBackFromPng() {
        SealedImage sealed = service.encode(portrait(), "  Ahmet Yılmaz  ", "2026001", "Bilgisayar Mühendisliği");
        InspectResult result = service.inspect(sealed.png());

        assertEquals("{\"no\":\"2026001\",\"ad\":\"Ahmet Yılmaz\",\"bolum\":\"Bilgisayar Mühendisliği\",\"dersler\":[]}", result.json());
        assertTrue(result.json().getBytes(StandardCharsets.UTF_8).length > result.json().length());
        assertTrue(result.gecerli());
        assertEquals("Ahmet Yılmaz", result.ad());
        assertEquals("2026001", result.no());
        assertTrue(sealed.jsonBytes() > 0);
    }

    /**
     * Küçük tuval uzar, mühür yine okunur.
     * Gelen: 2x2 PNG. Dışarı yükseklik 2'den büyüktür, numara 2026001 kalır.
     */
    @Test
    void growsACanvasThatCannotHoldThePayload() {
        BufferedImage tiny = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        SealedImage sealed = service.encode(RgbImages.toPng(tiny), "Ahmet Yılmaz", "2026001", "Bilgisayar Mühendisliği");

        assertEquals(2, sealed.width());
        assertTrue(sealed.height() > 2);
        assertTrue(service.inspect(sealed.png()).gecerli());
    }

    /**
     * Dersler sırayla eklenir.
     * Gelen önce puan 85, sonra puan 70.
     * Dışarı liste [85, 70].
     */
    @Test
    void appendsCoursesInOrder() {
        byte[] png = service.encode(portrait(), "Ahmet Yılmaz", "2026001", "Bilgisayar Mühendisliği").png();
        png = service.appendCourse(png, "Veri Yapıları", 85).png();
        png = service.appendCourse(png, "İşletim Sistemleri", 70).png();
        InspectResult result = service.inspect(png);

        assertEquals(2, result.dersler().size());
        assertEquals(85, result.dersler().get(0).puan());
        assertEquals(70, result.dersler().get(1).puan());
    }

    /**
     * Tek not 70 iken sahte kopya 71 okunur ve mühür düşer.
     * Orijinal PNG 70 ve geçerli kalır.
     */
    @Test
    void tamperTurnsSeventyIntoSeventyOne() {
        byte[] original = service.encode(portrait(), "Ahmet Yılmaz", "2026001", "Bilgisayar Mühendisliği").png();
        original = service.appendCourse(original, "İşletim Sistemleri", 70).png();
        byte[] forged = service.tamperOneDigit(original);
        InspectResult forgedResult = service.inspect(forged);

        assertEquals(70, service.inspect(original).dersler().get(0).puan());
        assertTrue(service.inspect(original).gecerli());
        assertFalse(forgedResult.gecerli());
        assertEquals(Messages.TAMPER, forgedResult.mesaj());
        assertEquals(71, forgedResult.dersler().get(0).puan());
    }

    /**
     * İki not varken ilk puanın son rakamı değişir.
     * Gelen 85 ve 70. Dışarı 84 ve 70, mühür geçersiz.
     */
    @Test
    void tamperChangesOnlyTheFirstGradeDigit() {
        byte[] png = service.encode(portrait(), "Ahmet Yılmaz", "2026001", "Bilgisayar Mühendisliği").png();
        png = service.appendCourse(png, "Veri Yapıları", 85).png();
        png = service.appendCourse(png, "İşletim Sistemleri", 70).png();
        InspectResult forged = service.inspect(service.tamperOneDigit(png));

        assertEquals(84, forged.dersler().get(0).puan());
        assertEquals(70, forged.dersler().get(1).puan());
        assertFalse(forged.gecerli());
    }

    /**
     * JSON gömülmemiş vesikalık okunabilir kayıt sayılmaz.
     */
    @Test
    void plainPortraitHasNoPayload() {
        InspectResult result = service.inspect(portrait());
        assertFalse(result.gecerli());
        assertEquals(Messages.UNREADABLE, result.mesaj());
    }

    private static byte[] portrait() {
        return RgbImages.samplePortrait("Ahmet Yılmaz");
    }
}
