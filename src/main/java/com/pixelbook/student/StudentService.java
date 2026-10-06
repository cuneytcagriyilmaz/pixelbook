package com.pixelbook.student;

import com.pixelbook.Messages;
import com.pixelbook.audit.AuditService;
import com.pixelbook.stego.CourseGrade;
import com.pixelbook.stego.InspectResult;
import com.pixelbook.stego.SealedImage;
import com.pixelbook.stego.SmartImageService;
import com.pixelbook.stego.StegoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Collator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Akıllı resmi öğrenci satırına bağlar. Kimlik ve karne resmin içinden okunur, form kutusundan alınmaz.
 */
@Service
public class StudentService {

    private final StudentRepository students;
    private final SmartImageService images;
    private final AuditService audit;

    /**
     * Depoyu, görüntü hizmetini ve denetim defterini bağlar.
     * Gelen: StudentRepository, SmartImageService, AuditService.
     * İçeride: üç alan saklanır.
     * Dışarı: register, search, photo, addCourse, revise, remove, restore ve purge çağrılabilir.
     */
    public StudentService(StudentRepository students, SmartImageService images, AuditService audit) {
        this.students = students;
        this.images = images;
        this.audit = audit;
    }

    /**
     * 2. ekran. Bırakılan akıllı resmi tabloya yazar.
     * Gelen: mühürlü PNG baytları, içinde no "2026001".
     * İçeride: imza doğrulanır, numara her vesikalığın içinden aranır. Tabloda numara kolonu yoktur.
     * Numara yoksa yeni satır INSERT edilir.
     * Dışarı: "Ahmet Yılmaz (2026001) - Bilgisayar Mühendisliği kaydı başarıyla oluşturuldu!" ve yeniKayit true.
     * Aynı PNG ikinci kez gelirse yeni satır açılmaz, smart_photo kolonu bu baytlarla güncellenir, yeniKayit false olur.
     * İmza tutmuyorsa satır yazılmaz.
     */
    @Transactional
    public RegisterResult register(byte[] photo) {
        InspectResult result = images.inspect(photo);
        if (!result.gecerli()) {
            throw new StegoException(result.mesaj() + " Kayıt yapılmadı.");
        }
        Student student = rowOf(result.no()).orElseGet(Student::new);
        boolean fresh = student.getId() == null;
        boolean fromTrash = student.getDeletedAt() != null;
        student.setSmartPhoto(photo);
        student.setDeletedAt(null);
        students.save(student);
        String mesaj = fresh
                ? Messages.registered(result.ad(), result.no(), result.bolum())
                : fromTrash
                ? Messages.restored(result.ad(), result.no())
                : Messages.updated(result.ad(), result.no(), result.bolum());
        audit.record(fresh ? "kayit" : "guncelle", result.no(), mesaj);
        return new RegisterResult(mesaj, result.no(), result.ad(), result.bolum(), fresh);
    }

    /**
     * Kayıt defterini resimlerden süzer, dizer ve sayfalar.
     * Gelen: q "Yılmaz", bolum "Bilgisayar Mühendisliği", sirala "ad", yon "asc", sayfa 1, boyut 8, cop false.
     * İçeride: çöpte olmayan satırların PNG'si inspect edilir. Arama yalnız karşılaştırmada katlanır, kayıtlı ad "ELİF" olarak kalır.
     * Dışarı: ogrenciler sayfanın dilimi, toplam süzülen sayı, bolumler açılır listenin bölüm adları.
     * hepsi true ise sayfa kesilmez, transkriptin açılır listesi herkesi görür.
     */
    @Transactional(readOnly = true)
    public StudentPage search(String q, String bolum, String sirala, String yon, int sayfa, int boyut, boolean cop, boolean hepsi) {
        List<StudentView> all = students.findAll().stream()
                .filter(student -> cop == (student.getDeletedAt() != null))
                .map(this::viewOf)
                .toList();
        List<String> bolumler = all.stream().map(StudentView::bolum).filter(value -> value != null && !value.isBlank())
                .distinct().sorted(turkish()).toList();
        List<StudentView> filtered = all.stream()
                .filter(view -> matches(view, q))
                .filter(view -> bolum == null || bolum.isBlank() || bolum.equals(view.bolum()))
                .sorted(order(sirala, yon))
                .toList();
        int size = hepsi ? Math.max(filtered.size(), 1) : Math.min(Math.max(boyut, 1), 50);
        int page = hepsi ? 1 : Math.max(sayfa, 1);
        int from = (page - 1) * size;
        List<StudentView> slice = from >= filtered.size() ? List.of() : filtered.subList(from, Math.min(from + size, filtered.size()));
        return new StudentPage(slice, filtered.size(), page, size, bolumler);
    }

    /**
     * Tek öğrenciyi vesikalığından okur.
     * Gelen numara: "2026001".
     * İçeride: satır bulunur, PNG inspect edilir.
     * Dışarı: ad "Ahmet Yılmaz", dersler resmin içinden, cop false. Numara yoksa kayıtlı değil hatası.
     */
    @Transactional(readOnly = true)
    public StudentView one(String studentNo) {
        return viewOf(findAny(studentNo));
    }

    /**
     * Sistemde duran akıllı fotoğrafı verir.
     * Gelen numara: "2026001".
     * İçeride: vesikalığın içindeki numara eşleşen satırın smart_photo kolonu okunur.
     * Dışarı: PNG baytları. Numara yoksa "2026001 numaralı öğrenci kayıtlı değil." fırlatılır.
     */
    @Transactional(readOnly = true)
    public byte[] photo(String studentNo) {
        return findAny(studentNo).getSmartPhoto();
    }

    /**
     * 3. ekran. Seçilen öğrencinin fotoğrafına bir ders gömer ve kolonu yeniler.
     * Gelen: no "2026001", ders "İşletim Sistemleri", puan 70. Kolondaki eski listede Veri Yapıları 85 vardır.
     * İçeride: appendCourse yeni PNG üretir, smart_photo bu baytlarla değişir.
     * Dışarı: mesaj "İşletim Sistemleri notu 70 olarak görsele işlendi." ve iki elemanlı dersler listesi.
     */
    @Transactional
    public CourseUpdate addCourse(String studentNo, String ders, int puan) {
        Student student = find(studentNo);
        SealedImage sealed = images.appendCourse(student.getSmartPhoto(), ders, puan);
        student.setSmartPhoto(sealed.png());
        students.save(student);
        InspectResult current = images.inspect(sealed.png());
        String mesaj = Messages.courseAdded(ders.trim(), puan);
        audit.record("not-ekle", studentNo, mesaj);
        return new CourseUpdate(mesaj, current.dersler(),
                sealed.width(), sealed.height(), sealed.jsonBytes());
    }

    /**
     * Kayıt defteri. Bırakılan vesikalığın kimliğini yeniler, karneyi korur, kolonu yeni PNG yapar.
     * Gelen: mühürlü PNG içinde no "2026999" ve Veri Yapıları 85. Form ad "ELİF DEMİR", no "2026998", bölüm "Yazılım Mühendisliği".
     * İçeride: imza doğrulanır, satır resmin içindeki eski numaradan bulunur, yeni kimlik piksellerin üstüne yazılır.
     * Dışarı: yeni PNG. Tabloda yalnız bu baytlar durur. İçinde no "2026998", ad "ELİF DEMİR", ders puanı hâlâ 85.
     * Yeni numara başka bir resmin içindeyse satır yazılmaz. Mühür bozuksa satır yazılmaz.
     */
    @Transactional
    public SealedImage revise(byte[] photo, String ad, String no, String bolum) {
        InspectResult result = images.inspect(photo);
        if (!result.gecerli()) {
            throw new StegoException(result.mesaj() + " Kayıt güncellenmedi.");
        }
        Student student = find(result.no());
        SealedImage sealed = images.rewriteIdentity(photo, ad, no, bolum);
        InspectResult next = images.inspect(sealed.png());
        boolean taken = rowOf(next.no()).filter(other -> !other.getId().equals(student.getId())).isPresent();
        if (taken) {
            throw new StegoException(next.no() + " numarası başka bir öğrenciye ait.");
        }
        student.setSmartPhoto(sealed.png());
        students.save(student);
        audit.record("kimlik", next.no(), Messages.identityRevised(next.ad(), next.no(), next.bolum()));
        return sealed;
    }

    /**
     * Kayıt defteri. Bırakılan vesikalığın numarasıyla satırı siler.
     * Gelen: mühürlü PNG, içinde no "2026001", ad "Ahmet Yılmaz".
     * İçeride: imza doğrulanır, numara resmin içinden okunur, o resmi taşıyan satır çöpe alınır.
     * Dışarı: "Ahmet Yılmaz (2026001) kaydı silindi." Mühür bozuksa satır durur.
     */
    @Transactional
    public RemoveResult remove(byte[] photo) {
        InspectResult result = images.inspect(photo);
        if (!result.gecerli()) {
            throw new StegoException(result.mesaj() + " Kayıt silinmedi.");
        }
        Student student = find(result.no());
        student.setDeletedAt(Instant.now());
        students.save(student);
        String mesaj = Messages.removed(result.ad(), result.no());
        audit.record("cope-at", result.no(), mesaj);
        return new RemoveResult(mesaj, result.no(), result.ad(), result.bolum());
    }

    /**
     * Seçili vesikalıkları tek tek çöpe atar. Biri bozuksa diğerleri durmaz.
     * Gelen: iki PNG. İlki no "2026001", ikincisi mühürü bozuk.
     * İçeride: her dosya için remove denenir. StegoException yutulur, mesaj reddedilen listesine yazılır.
     * Dışarı: silinen ["Ahmet Yılmaz (2026001) kaydı silindi."], reddedilen [kritik uyarı].
     */
    @Transactional
    public BulkRemove removeMany(List<byte[]> photos) {
        List<String> silinen = new ArrayList<>();
        List<String> reddedilen = new ArrayList<>();
        for (byte[] photo : photos) {
            try {
                silinen.add(remove(photo).mesaj());
            } catch (StegoException ex) {
                reddedilen.add(ex.getMessage());
            }
        }
        return new BulkRemove(silinen, reddedilen);
    }

    /**
     * Çöpteki vesikalığı deftere geri alır.
     * Gelen: mühürlü PNG, no "2026001", satırın deletedAt alanı dolu.
     * İçeride: imza doğrulanır, deletedAt null yapılır. PNG silinmez.
     * Dışarı: "Ahmet Yılmaz (2026001) kaydı çöp kutusundan çıkarıldı."
     * Kayıt zaten defterdeyse metot işlemez.
     */
    @Transactional
    public RemoveResult restore(byte[] photo) {
        InspectResult result = images.inspect(photo);
        if (!result.gecerli()) {
            throw new StegoException(result.mesaj() + " Kayıt geri alınmadı.");
        }
        Student student = findAny(result.no());
        if (student.getDeletedAt() == null) {
            throw new StegoException(result.no() + " numaralı kayıt zaten defterde.");
        }
        student.setDeletedAt(null);
        students.save(student);
        String mesaj = Messages.restored(result.ad(), result.no());
        audit.record("geri-al", result.no(), mesaj);
        return new RemoveResult(mesaj, result.no(), result.ad(), result.bolum());
    }

    /**
     * Çöpteki kaydı ve PNG'sini kalıcı siler.
     * Gelen: mühürlü PNG, no "2026001", satır çöpte.
     * İçeride: imza doğrulanır, satır DELETE edilir.
     * Dışarı: "Ahmet Yılmaz (2026001) kaydı kalıcı olarak silindi."
     * Kayıt çöpte değilse "Önce vesikalığıyla silin." Satır durur.
     */
    @Transactional
    public RemoveResult purge(byte[] photo) {
        InspectResult result = images.inspect(photo);
        if (!result.gecerli()) {
            throw new StegoException(result.mesaj() + " Kayıt silinmedi.");
        }
        Student student = findAny(result.no());
        if (student.getDeletedAt() == null) {
            throw new StegoException(result.no() + " numaralı kayıt çöp kutusunda değil. Önce vesikalığıyla silin.");
        }
        students.delete(student);
        String mesaj = Messages.purged(result.ad(), result.no());
        audit.record("kalici-sil", result.no(), mesaj);
        return new RemoveResult(mesaj, result.no(), result.ad(), result.bolum());
    }

    /**
     * Kayıt defteri. Seçilen resmin bir ders satırını değiştirir ve kolonu yeniler.
     * Gelen: no "2026001", index 0, ders "Veri Yapıları ve Algoritmalar", puan 90. Eski puan 85.
     * İçeride: kolondaki PNG açılır, 0. satırın puanı 90 olur, mühür yenilenir.
     * Dışarı: "Veri Yapıları ve Algoritmalar notu 90 olarak güncellendi." ve dersler listesinde puan 90.
     */
    @Transactional
    public CourseUpdate replaceCourse(String studentNo, int index, String ders, int puan) {
        Student student = find(studentNo);
        SealedImage sealed = images.replaceCourse(student.getSmartPhoto(), index, ders, puan);
        student.setSmartPhoto(sealed.png());
        students.save(student);
        InspectResult current = images.inspect(sealed.png());
        CourseGrade row = current.dersler().get(index);
        String mesaj = Messages.courseChanged(row.ders(), row.puan());
        audit.record("not-guncelle", studentNo, mesaj);
        return new CourseUpdate(mesaj, current.dersler(),
                sealed.width(), sealed.height(), sealed.jsonBytes());
    }

    /**
     * Ders satırını sabit koduyla günceller.
     * Gelen: no "2026001", kod "k1", ders "Veri Yapıları ve Algoritmalar", puan 90.
     * İçeride: PNG açılır, k1'in puanı 90 olur, kod k1 kalır, mühür yenilenir.
     * Dışarı: "Veri Yapıları ve Algoritmalar notu 90 olarak güncellendi."
     */
    @Transactional
    public CourseUpdate replaceCourseByCode(String studentNo, String kod, String ders, int puan) {
        Student student = find(studentNo);
        SealedImage sealed = images.replaceCourseByCode(student.getSmartPhoto(), kod, ders, puan);
        student.setSmartPhoto(sealed.png());
        students.save(student);
        InspectResult current = images.inspect(sealed.png());
        CourseGrade row = current.dersler().stream().filter(course -> kod.trim().equals(course.kod())).findFirst()
                .orElse(current.dersler().isEmpty() ? new CourseGrade(ders, puan) : current.dersler().get(0));
        String mesaj = Messages.courseChanged(row.ders(), row.puan());
        audit.record("not-guncelle", studentNo, mesaj);
        return new CourseUpdate(mesaj, current.dersler(), sealed.width(), sealed.height(), sealed.jsonBytes());
    }

    /**
     * Kayıt defteri. Seçilen resmin bir ders satırını çıkarır ve kolonu yeniler.
     * Gelen: no "2026001", index 0, listedeki ilk ders "Veri Yapıları ve Algoritmalar".
     * İçeride: o satır düşer, kalan listeyle PNG yeniden mühürlenir.
     * Dışarı: "Veri Yapıları ve Algoritmalar notu karneiden silindi." Kalan dersler döner. Son dersse liste [].
     */
    @Transactional
    public CourseUpdate removeCourse(String studentNo, int index) {
        Student student = find(studentNo);
        InspectResult before = images.inspect(student.getSmartPhoto());
        if (!before.gecerli()) {
            throw new StegoException(before.mesaj() + " Not silinmedi.");
        }
        if (index < 0 || index >= before.dersler().size()) {
            throw new StegoException(index + " numaralı ders satırı yok. Karne " + before.dersler().size() + " satır.");
        }
        String ders = before.dersler().get(index).ders();
        SealedImage sealed = images.removeCourse(student.getSmartPhoto(), index);
        student.setSmartPhoto(sealed.png());
        students.save(student);
        InspectResult current = images.inspect(sealed.png());
        String mesaj = Messages.courseRemoved(ders);
        audit.record("not-sil", studentNo, mesaj);
        return new CourseUpdate(mesaj, current.dersler(),
                sealed.width(), sealed.height(), sealed.jsonBytes());
    }

    /**
     * Ders satırını sabit koduyla çıkarır.
     * Gelen: no "2026001", kod "k1". O satır "Veri Yapıları ve Algoritmalar".
     * İçeride: k1 düşer, kalan kodlar yerinde kalır, PNG yeniden mühürlenir.
     * Dışarı: "Veri Yapıları ve Algoritmalar notu karneiden silindi."
     */
    @Transactional
    public CourseUpdate removeCourseByCode(String studentNo, String kod) {
        Student student = find(studentNo);
        InspectResult before = images.inspect(student.getSmartPhoto());
        if (!before.gecerli()) {
            throw new StegoException(before.mesaj() + " Not silinmedi.");
        }
        String ders = before.dersler().stream().filter(course -> kod.equals(course.kod())).map(CourseGrade::ders)
                .findFirst().orElse(kod);
        SealedImage sealed = images.removeCourseByCode(student.getSmartPhoto(), kod);
        student.setSmartPhoto(sealed.png());
        students.save(student);
        InspectResult current = images.inspect(sealed.png());
        String mesaj = Messages.courseRemoved(ders);
        audit.record("not-sil", studentNo, mesaj);
        return new CourseUpdate(mesaj, current.dersler(), sealed.width(), sealed.height(), sealed.jsonBytes());
    }

    /**
     * Numarayla satırı bulur.
     * Gelen: "2026001".
     * İçeride: her vesikalık açılır, içindeki no bu numaraysa satır seçilir.
     * Dışarı: Student. Yoksa "2026001 numaralı öğrenci kayıtlı değil. Önce hızlı kayıt ekranına bırakın."
     */
    private Student find(String studentNo) {
        Student student = findAny(studentNo);
        if (student.getDeletedAt() != null) {
            throw new StegoException(studentNo + " numaralı öğrenci çöp kutusunda.");
        }
        return student;
    }

    /**
     * Numarayla satırı, çöpte olsa da bulur.
     * Gelen: "2026001".
     * İçeride: her vesikalık açılır. deletedAt bakılmaz.
     * Dışarı: Student. Yoksa "2026001 numaralı öğrenci kayıtlı değil. Önce hızlı kayıt ekranına bırakın."
     */
    private Student findAny(String studentNo) {
        return rowOf(studentNo)
                .orElseThrow(() -> new StegoException(
                        studentNo + " numaralı öğrenci kayıtlı değil. Önce hızlı kayıt ekranına bırakın."));
    }

    /**
     * Numarayı vesikalığın içinden arar.
     * Gelen: "2026001", tabloda iki PNG. İkincinin içinde no "2026001".
     * İçeride: inspect. Kolonda numara yoktur.
     * Dışarı: ikinci satır. Hiçbiri tutmazsa boş Optional.
     */
    private Optional<Student> rowOf(String studentNo) {
        return students.findAll().stream()
                .filter(row -> studentNo.equals(images.inspect(row.getSmartPhoto()).no()))
                .findFirst();
    }

    /**
     * Satırı, vesikalığın içindeki karneyle birlikte görüşe çevirir.
     * Gelen: PNG içinde no "2026001", ad "Ahmet Yılmaz", deletedAt null.
     * İçeride: inspect. Numara, ad ve bölüm yalnız resimden okunur.
     * Dışarı: no "2026001", cop false. "ELİF" resimde nasıl duruyorsa öyle kalır.
     */
    private StudentView viewOf(Student student) {
        InspectResult result = images.inspect(student.getSmartPhoto());
        List<CourseGrade> dersler = result.dersler() == null ? List.of() : result.dersler();
        return new StudentView(result.no(), result.ad(), result.bolum(), dersler, result.gecerli(), student.getDeletedAt() != null);
    }

    /**
     * Arama iğnesi ad, numara, bölüm veya dersin içinde geçiyor mu bakar.
     * Gelen: ad "ELİF DEMİR", q "elif".
     * İçeride: yalnız karşılaştırma kopyası Türkçe küçük harfe katlanır. Kayıttaki ad değişmez.
     * Dışarı: true. q boşsa true. q "ALI" ve ad "Veli" ise false.
     */
    private static boolean matches(StudentView view, String q) {
        if (q == null || q.isBlank()) {
            return true;
        }
        String needle = q.trim().toLowerCase(Locale.forLanguageTag("tr"));
        if (contains(view.ad(), needle) || contains(view.no(), needle) || contains(view.bolum(), needle)) {
            return true;
        }
        return view.dersler() != null && view.dersler().stream()
                .anyMatch(course -> contains(course.ders(), needle) || contains(course.kod(), needle));
    }

    /**
     * Metnin katlanmış kopyasında iğneyi arar.
     * Gelen: value "ELİF", needle "elif".
     * İçeride: value'nun kopyası küçülür, asıl metin durur.
     * Dışarı: true. value null ise false.
     */
    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.forLanguageTag("tr")).contains(needle);
    }

    /**
     * Sıralama ölçütünü kurar.
     * Gelen: sirala "ad", yon "desc".
     * İçeride: Türkçe Collator. Bilinmeyen sirala numaraya düşer.
     * Dışarı: ada göre ters karşılaştırıcı. "Ç" , "C" den sonra gelir.
     */
    private static Comparator<StudentView> order(String sirala, String yon) {
        Collator collator = turkish();
        Comparator<StudentView> comparator = switch (sirala == null ? "" : sirala) {
            case "ad" -> Comparator.comparing(StudentView::ad, Comparator.nullsLast(collator));
            case "bolum" -> Comparator.comparing(StudentView::bolum, Comparator.nullsLast(collator));
            default -> Comparator.comparing(StudentView::no, Comparator.nullsLast(collator));
        };
        return "desc".equals(yon) ? comparator.reversed() : comparator;
    }

    /**
     * Türkçe sıra verir.
     * Gelen: "Çağla" ve "Can".
     * İçeride: tr dilinin Collator'ü.
     * Dışarı: "Can" önce, "Çağla" sonra.
     */
    private static Collator turkish() {
        Collator collator = Collator.getInstance(Locale.forLanguageTag("tr"));
        collator.setStrength(Collator.PRIMARY);
        return collator;
    }

}
