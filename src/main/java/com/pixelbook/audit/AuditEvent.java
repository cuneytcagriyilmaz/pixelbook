package com.pixelbook.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Kim, hangi öğrencinin resminde ne değiştirdi.
 * Gelen: kullanici "Ayşe Memur", islem "cope-at", ogrenciNo "2026001", ozet "Ahmet Yılmaz (2026001) kaydı silindi."
 * Dışarı: aynı metinler. "ALI" küçülmez.
 */
@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant zaman;

    @Column(nullable = false)
    private String kullanici;

    @Column(nullable = false)
    private String islem;

    @Column(nullable = false)
    private String ogrenciNo;

    @Column(nullable = false, length = 500)
    private String ozet;

    /**
     * Boş denetim satırı.
     * Gelen: argüman yok.
     * İçeride: id null.
     * Dışarı: henüz kaydedilmemiş AuditEvent.
     */
    public AuditEvent() {
    }

    /**
     * Satırın veritabanı kimliğini verir.
     * Gelen: kayıt sonrası id 4.
     * Dışarı: 4.
     */
    public Long getId() {
        return id;
    }

    /**
     * Anı verir.
     * Gelen: now.
     * Dışarı: aynı Instant.
     */
    public Instant getZaman() {
        return zaman;
    }

    /**
     * Anı yazar.
     * Gelen: 2026-10-06T08:00:00Z.
     * İçeride: zaman alanı aynı an olur.
     * Dışarı: getZaman() aynı anı döner.
     */
    public void setZaman(Instant zaman) {
        this.zaman = zaman;
    }

    /**
     * İşlemi yapanın adını verir.
     * Gelen kolon: "Ayşe Memur".
     * Dışarı: "Ayşe Memur".
     */
    public String getKullanici() {
        return kullanici;
    }

    /**
     * İşlemi yapanın adını yazar.
     * Gelen: "Ayşe Memur".
     * İçeride: metin olduğu gibi durur, küçültülmez.
     * Dışarı: getKullanici() "Ayşe Memur". "ALI" gelirse "ALI" kalır.
     */
    public void setKullanici(String kullanici) {
        this.kullanici = kullanici;
    }

    /**
     * İşlem kodunu verir.
     * Gelen kolon: "cope-at".
     * Dışarı: "cope-at".
     */
    public String getIslem() {
        return islem;
    }

    /**
     * İşlem kodunu yazar.
     * Gelen: "not-ekle".
     * İçeride: kod olduğu gibi durur.
     * Dışarı: getIslem() "not-ekle".
     */
    public void setIslem(String islem) {
        this.islem = islem;
    }

    /**
     * Öğrenci numarasını verir.
     * Gelen kolon: "2026001".
     * Dışarı: "2026001".
     */
    public String getOgrenciNo() {
        return ogrenciNo;
    }

    /**
     * Öğrenci numarasını yazar.
     * Gelen: "2026001".
     * İçeride: numara olduğu gibi durur.
     * Dışarı: getOgrenciNo() "2026001".
     */
    public void setOgrenciNo(String ogrenciNo) {
        this.ogrenciNo = ogrenciNo;
    }

    /**
     * Ekranda görünen cümleyi verir.
     * Gelen kolon: "Ahmet Yılmaz (2026001) kaydı silindi."
     * Dışarı: aynı cümle.
     */
    public String getOzet() {
        return ozet;
    }

    /**
     * Ekranda görünen cümleyi yazar.
     * Gelen: "Veri Yapıları ve Algoritmalar notu 85 olarak görsele işlendi."
     * İçeride: cümle kesilmeden saklanır, 500 karaktere kadar.
     * Dışarı: getOzet() aynı cümle.
     */
    public void setOzet(String ozet) {
        this.ozet = ozet;
    }
}
