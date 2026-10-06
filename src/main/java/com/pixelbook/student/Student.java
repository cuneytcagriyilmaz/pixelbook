package com.pixelbook.student;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Kayıt ekranının yazdığı satır. Numara, ad, bölüm ve ders notları kolon değildir; akıllı fotoğrafın içindedir.
 */
@Entity
@Table(name = "students")
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.BLOB)
    @Column(nullable = false)
    private byte[] smartPhoto;

    @Column
    private Instant deletedAt;

    /**
     * Yeni, henüz tabloya yazılmamış satır.
     * Gelen: kurucu argümanı yoktur.
     * İçeride: id null, diğer alanlar null.
     * Dışarı: boş Student. id() null döner.
     */
    public Student() {
    }

    /**
     * Satırın veritabanı kimliğini verir.
     * Gelen: kayıt sonrası id 1.
     * Dışarı: 1. Henüz save edilmemiş satırda null.
     */
    public Long getId() {
        return id;
    }

    /**
     * Saklanan akıllı PNG baytlarını verir.
     * Gelen kolon: encode çıktısının baytları.
     * Dışarı: aynı bayt dizisi. İlk sekiz bayt PNG imzasıdır.
     */
    public byte[] getSmartPhoto() {
        return smartPhoto;
    }

    /**
     * Akıllı PNG baytlarını kolona yazar.
     * Gelen: mühürlü PNG, örnek uzunluk 15234 bayt.
     * İçeride: dizi olduğu gibi BLOB kolona konur, piksel yeniden kodlanmaz.
     * Dışarı: getSmartPhoto() aynı diziyi döndürür.
     */
    public void setSmartPhoto(byte[] smartPhoto) {
        this.smartPhoto = smartPhoto;
    }

    /**
     * Çöpe atılma anını verir.
     * Gelen kolon: null ise kayıt defterdedir. Doluysa çöp kutusundadır.
     * Dışarı: null ya da o an.
     */
    public Instant getDeletedAt() {
        return deletedAt;
    }

    /**
     * Çöpe atılma anını yazar.
     * Gelen: Instant.now. Geri alınınca null.
     * İçeride: deletedAt aynı değer olur. Satır ve PNG silinmez.
     * Dışarı: getDeletedAt() aynı anı, geri alınınca null döner.
     */
    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
