package com.pixelbook.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Resimde olan her değişikliği adıyla birlikte yazar.
 */
@Service
public class AuditService {

    private final AuditRepository events;

    /**
     * Denetim deposunu bağlar.
     * Gelen: AuditRepository.
     * İçeride: tek alan saklanır.
     * Dışarı: record ve latest çağrılabilir.
     */
    public AuditService(AuditRepository events) {
        this.events = events;
    }

    /**
     * Bir resim işlemini denetim defterine ekler.
     * Gelen: islem "not-ekle", no "2026001", ozet "Veri Yapıları ve Algoritmalar notu 85 olarak görsele işlendi."
     * İçeride: kullanici "sistem" yazılır. Zaman Instant.now.
     * Dışarı: audit_events tablosunda yeni satır. Ad "ALI" ise "ALI" kalır.
     */
    @Transactional
    public void record(String islem, String no, String ozet) {
        AuditEvent event = new AuditEvent();
        event.setZaman(Instant.now());
        event.setKullanici("sistem");
        event.setIslem(islem);
        event.setOgrenciNo(no == null ? "" : no);
        event.setOzet(ozet);
        events.save(event);
    }

    /**
     * Son işlemleri döker.
     * Gelen: tabloda iki satır.
     * İçeride: son 40 satır okunur.
     * Dışarı: önce en yeni. kullanici "sistem".
     */
    @Transactional(readOnly = true)
    public List<AuditView> latest() {
        return events.findTop40ByOrderByIdDesc().stream()
                .map(event -> new AuditView(event.getZaman(), event.getKullanici(), event.getIslem(),
                        event.getOgrenciNo(), event.getOzet()))
                .toList();
    }
}
