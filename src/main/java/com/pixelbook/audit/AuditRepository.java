package com.pixelbook.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Denetim satırlarını okur ve yazar.
 */
public interface AuditRepository extends JpaRepository<AuditEvent, Long> {

    /**
     * Son kırk işlemi yeniden eskiye dizer.
     * Gelen: id 1 kayit, id 2 not-ekle.
     * İçeride: order by id desc, ilk 40.
     * Dışarı: önce id 2, sonra id 1.
     */
    List<AuditEvent> findTop40ByOrderByIdDesc();
}
