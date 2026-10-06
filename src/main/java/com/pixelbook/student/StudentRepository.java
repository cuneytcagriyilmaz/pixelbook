package com.pixelbook.student;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * students tablosuna erişir. Numara, ad, bölüm ve ders listesi burada sorgulanmaz; hepsi fotoğrafın içindedir.
 */
public interface StudentRepository extends JpaRepository<Student, Long> {
}
