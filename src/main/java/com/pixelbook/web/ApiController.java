package com.pixelbook.web;

import com.pixelbook.audit.AuditService;
import com.pixelbook.audit.AuditView;
import com.pixelbook.stego.InspectResult;
import com.pixelbook.stego.RgbImages;
import com.pixelbook.stego.SealedImage;
import com.pixelbook.stego.SmartImageService;
import com.pixelbook.student.BulkRemove;
import com.pixelbook.student.CourseUpdate;
import com.pixelbook.student.RegisterResult;
import com.pixelbook.student.RemoveResult;
import com.pixelbook.student.StudentPage;
import com.pixelbook.student.StudentService;
import com.pixelbook.student.StudentView;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Ekranların HTTP kapısı. Tarayıcı buraya fotoğraf bırakır, JSON ya da PNG geri alır.
 */
@RestController
public class ApiController {

    private final SmartImageService images;
    private final StudentService students;
    private final AuditService audit;

    /**
     * Hizmetleri bağlar.
     * Gelen: SmartImageService, StudentService, AuditService.
     * İçeride: üç alan saklanır.
     * Dışarı: /api uçları cevap vermeye hazır denetleyici.
     */
    public ApiController(SmartImageService images, StudentService students, AuditService audit) {
        this.images = images;
        this.students = students;
        this.audit = audit;
    }

    /**
     * Kamera dosyası yokken 1. ekranın kullandığı düz vesikalığı üretir.
     * Gelen: ad "Ahmet Yılmaz".
     * İçeride: RgbImages.samplePortrait lacivert kart çizer. JSON gömülmez.
     * Dışarı: image/png, dosya adı ornek_vesikalik.png.
     */
    @GetMapping("/api/sample-portrait")
    public ResponseEntity<byte[]> samplePortrait(@RequestParam(defaultValue = "ORNEK") String ad) {
        return png(RgbImages.samplePortrait(ad), "ornek_vesikalik.png", 0, 0, 0);
    }

    /**
     * 1. ekran. Fotoğraf ve formu akıllı PNG'ye çevirip indirtir.
     * Gelen multipart: photo dosyası, ad "Ahmet Yılmaz", no "2026001", bolum "Bilgisayar Mühendisliği".
     * İçeride: encode JSON'u kurar, HMAC ekler, LSB'ye yazar.
     * Dışarı: image/png, dosya adı 2026001_akilli_vesikalik.png.
     * Başlıklarda tuval genişliği, yüksekliği ve JSON bayt sayısı da durur.
     */
    @PostMapping(value = "/api/encode", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> encode(
            @RequestParam("photo") MultipartFile photo,
            @RequestParam("ad") String ad,
            @RequestParam("no") String no,
            @RequestParam("bolum") String bolum) throws IOException {
        SealedImage sealed = images.encode(bytes(photo), ad, no, bolum);
        return png(sealed.png(), no.trim() + "_akilli_vesikalik.png",
                sealed.width(), sealed.height(), sealed.jsonBytes());
    }

    /**
     * 2. ekran. Form alanı yoktur, yalnız fotoğraf gelir.
     * Gelen: ahmet_akilli_vesikalik.png.
     * İçeride: piksellerden JSON okunur, imza tutuyorsa students tablosuna yazılır.
     * Dışarı: {"mesaj":"Ahmet Yılmaz (2026001) - Bilgisayar Mühendisliği kaydı başarıyla oluşturuldu!","no":"2026001",...}
     */
    @PostMapping(value = "/api/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RegisterResult register(@RequestParam("photo") MultipartFile photo) throws IOException {
        return students.register(bytes(photo));
    }

    /**
     * Kayıt defteri. Bütün vesikalıkları açar, süzer, dizer, sayfalar.
     * Gelen: q "Yılmaz", bolum "Bilgisayar Mühendisliği", sirala "ad", yon "asc", sayfa 1, boyut 8, cop false.
     * İçeride: her PNG inspect edilir. Not kolonundan okunmaz.
     * Dışarı: {"ogrenciler":[...],"toplam":1,"sayfa":1,"boyut":8,"bolumler":["Bilgisayar Mühendisliği"]}
     * hepsi true ise transkript açılır listesi kesilmez.
     */
    @GetMapping("/api/students")
    public StudentPage students(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "bolum", required = false) String bolum,
            @RequestParam(name = "sirala", defaultValue = "no") String sirala,
            @RequestParam(name = "yon", defaultValue = "asc") String yon,
            @RequestParam(name = "sayfa", defaultValue = "1") int sayfa,
            @RequestParam(name = "boyut", defaultValue = "8") int boyut,
            @RequestParam(name = "cop", defaultValue = "false") boolean cop,
            @RequestParam(name = "hepsi", defaultValue = "false") boolean hepsi) {
        return students.search(q, bolum, sirala, yon, sayfa, boyut, cop, hepsi);
    }

    /**
     * Tek öğrenciyi vesikalığından okur.
     * Gelen yol: /api/students/2026001.
     * İçeride: smart_photo inspect edilir.
     * Dışarı: {"no":"2026001","ad":"Ahmet Yılmaz","bolum":"Bilgisayar Mühendisliği","dersler":[...],"gecerli":true,"cop":false}
     */
    @GetMapping("/api/students/{no}")
    public StudentView student(@PathVariable("no") String no) {
        return students.one(no);
    }

    /**
     * Son resim işlemlerini döker.
     * Gelen: kayıt ve not işlemlerinden sonra biriken satırlar.
     * İçeride: son 40 denetim satırı okunur.
     * Dışarı: [{"kullanici":"sistem","islem":"not-ekle","no":"2026001","ozet":"..."}]
     */
    @GetMapping("/api/audit")
    public List<AuditView> audit() {
        return audit.latest();
    }

    /**
     * 3. ekranda görünen akıllı fotoğraf.
     * Gelen yol: /api/students/2026001/photo.
     * İçeride: smart_photo kolonu okunur.
     * Dışarı: image/png. Önbellek kapalıdır; ders eklendikten sonra tarayıcı eski kareyi tutmasın diye.
     */
    @GetMapping("/api/students/{no}/photo")
    public ResponseEntity<byte[]> photo(@PathVariable("no") String no) {
        return png(students.photo(no), no + "_akilli_vesikalik.png", 0, 0, 0);
    }

    /**
     * 3. ekran. Seçilen öğrencinin resmine bir ders işler.
     * Gelen: yol 2026001, gövde {"ders":"Veri Yapıları ve Algoritmalar","puan":85}.
     * İçeride: kolondaki PNG çözülür, diziye bu satır eklenir, yeni PNG kolona yazılır.
     * Dışarı: {"mesaj":"İşletim Sistemleri notu 70 olarak görsele işlendi.","dersler":[{"ders":"İşletim Sistemleri","puan":70}],"genislik":320,"yukseklik":400,"jsonBayt":127}
     * Aynı vesikalıkta boş karne 86 bayttı; bu ders eklenince JSON 127 bayta çıkar.
     */
    @PostMapping("/api/students/{no}/courses")
    public CourseUpdate addCourse(@PathVariable("no") String no, @RequestBody CourseRequest request) {
        return students.addCourse(no, request.ders(), request.puan());
    }

    /**
     * Kayıt defteri. Vesikalığın kimliğini yeniler, karneyi bırakır, güncel PNG'yi indirir.
     * Gelen multipart: photo mühürlü PNG (içinde no "2026999" ve Veri Yapıları 85), ad "ELİF DEMİR", no "2026998", bolum "Yazılım Mühendisliği".
     * İçeride: eski numaranın satırı bulunur, yeni kimlik piksellerin üstüne mühürlenir, smart_photo kolonu bu baytlar olur.
     * Dışarı: image/png, dosya adı 2026998_akilli_vesikalik.png. İçinde ad "ELİF DEMİR", puan hâlâ 85.
     * Başka bir öğrencinin numarası yazılırsa 400 döner ve kolon değişmez.
     */
    @PostMapping(value = "/api/revise", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> revise(
            @RequestParam("photo") MultipartFile photo,
            @RequestParam("ad") String ad,
            @RequestParam("no") String no,
            @RequestParam("bolum") String bolum) throws IOException {
        SealedImage sealed = students.revise(bytes(photo), ad, no, bolum);
        return png(sealed.png(), no.trim() + "_akilli_vesikalik.png",
                sealed.width(), sealed.height(), sealed.jsonBytes());
    }

    /**
     * Kayıt defteri. Silinecek kişiyi numara kutusundan değil, bırakılan vesikalıktan okur.
     * Gelen: mühürlü PNG, içinde no "2026001".
     * İçeride: imza tutuyorsa bu numaradaki satır silinir.
     * Dışarı: {"mesaj":"Ahmet Yılmaz (2026001) kaydı silindi.","no":"2026001","ad":"Ahmet Yılmaz","bolum":"Bilgisayar Mühendisliği"}
     * Mühür bozuksa satır durur ve 400 döner.
     */
    @PostMapping(value = "/api/remove", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RemoveResult remove(@RequestParam("photo") MultipartFile photo) throws IOException {
        return students.remove(bytes(photo));
    }

    /**
     * Seçili vesikalıkları çöpe atar. Numara listesi gelmez, dosyalar gelir.
     * Gelen: photo parçası iki kez. Biri 2026001, biri bozuk mühür.
     * İçeride: her dosya ayrı remove olur.
     * Dışarı: {"silinen":["Ahmet Yılmaz (2026001) kaydı silindi."],"reddedilen":["... Kayıt silinmedi."]}
     */
    @PostMapping(value = "/api/remove-many", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BulkRemove removeMany(@RequestParam("photo") List<MultipartFile> photos) throws IOException {
        List<byte[]> bodies = new ArrayList<>();
        for (MultipartFile photo : photos) {
            bodies.add(bytes(photo));
        }
        return students.removeMany(bodies);
    }

    /**
     * Çöpteki vesikalığı deftere geri koyar.
     * Gelen: mühürlü PNG, no "2026001", satır çöpte.
     * İçeride: deletedAt null yapılır, PNG durur.
     * Dışarı: {"mesaj":"Ahmet Yılmaz (2026001) kaydı çöp kutusundan çıkarıldı.","no":"2026001",...}
     */
    @PostMapping(value = "/api/restore", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RemoveResult restore(@RequestParam("photo") MultipartFile photo) throws IOException {
        return students.restore(bytes(photo));
    }

    /**
     * Çöpteki kaydı kalıcı siler.
     * Gelen: mühürlü PNG, no "2026001", satır çöpte.
     * İçeride: satır DELETE edilir.
     * Dışarı: {"mesaj":"Ahmet Yılmaz (2026001) kaydı kalıcı olarak silindi.","no":"2026001",...}
     * Kayıt çöpte değilse 400 ve "Önce vesikalığıyla silin."
     */
    @PostMapping(value = "/api/purge", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RemoveResult purge(@RequestParam("photo") MultipartFile photo) throws IOException {
        return students.purge(bytes(photo));
    }

    /**
     * Kayıt defteri. Seçilen resmin bir ders satırını değiştirir.
     * Gelen: yol 2026001 ve index 0, gövde {"ders":"Veri Yapıları ve Algoritmalar","puan":90}.
     * İçeride: kolondaki PNG açılır, 0. satırın puanı 90 yapılır, mühür yenilenir, kolon yeni PNG olur.
     * Dışarı: {"mesaj":"Veri Yapıları ve Algoritmalar notu 90 olarak güncellendi.","dersler":[{"ders":"Veri Yapıları ve Algoritmalar","puan":90}],...}
     */
    @PutMapping("/api/students/{no}/courses/{index}")
    public CourseUpdate replaceCourse(
            @PathVariable("no") String no,
            @PathVariable("index") int index,
            @RequestBody CourseRequest request) {
        return students.replaceCourse(no, index, request.ders(), request.puan());
    }

    /**
     * Kayıt defteri. Seçilen resmin bir ders satırını çıkarır.
     * Gelen: yol 2026001 ve index 0. O satır "Veri Yapıları ve Algoritmalar".
     * İçeride: satır düşer, kalan listeyle PNG yeniden mühürlenir, kolon değişir.
     * Dışarı: {"mesaj":"Veri Yapıları ve Algoritmalar notu karneiden silindi.","dersler":[]}
     * Index karneden büyükse 400 döner ve PNG değişmez.
     */
    @DeleteMapping("/api/students/{no}/courses/{index}")
    public CourseUpdate removeCourse(@PathVariable("no") String no, @PathVariable("index") int index) {
        return students.removeCourse(no, index);
    }

    /**
     * Ders satırını sabit koduyla günceller.
     * Gelen: yol 2026001 ve k1, gövde {"ders":"Veri Yapıları ve Algoritmalar","puan":90}.
     * İçeride: k1'in puanı 90 olur, kod k1 kalır, PNG yeniden mühürlenir.
     * Dışarı: {"mesaj":"Veri Yapıları ve Algoritmalar notu 90 olarak güncellendi.","dersler":[{"kod":"k1","ders":"Veri Yapıları ve Algoritmalar","puan":90}]}
     */
    @PutMapping("/api/students/{no}/notlar/{kod}")
    public CourseUpdate replaceCourseByCode(
            @PathVariable("no") String no,
            @PathVariable("kod") String kod,
            @RequestBody CourseRequest request) {
        return students.replaceCourseByCode(no, kod, request.ders(), request.puan());
    }

    /**
     * Ders satırını sabit koduyla çıkarır.
     * Gelen: yol 2026001 ve k1. O satır "Veri Yapıları ve Algoritmalar".
     * İçeride: k1 düşer, k2 durursa yerinde kalır, PNG yeniden mühürlenir.
     * Dışarı: {"mesaj":"Veri Yapıları ve Algoritmalar notu karneiden silindi.","dersler":[]}
     */
    @DeleteMapping("/api/students/{no}/notlar/{kod}")
    public CourseUpdate removeCourseByCode(@PathVariable("no") String no, @PathVariable("kod") String kod) {
        return students.removeCourseByCode(no, kod);
    }

    /**
     * 4. ekran. Şüpheli vesikalığı okur.
     * Gelen: orijinal PNG.
     * İçeride: LSB çözülür, HMAC yeniden hesaplanır.
     * Dışarı: {"gecerli":true,"mesaj":"İmza geçerli. ...","ad":"Ahmet Yılmaz","dersler":[...]}.
     * Bir rakamı bozulmuş kopyada gecerli false ve KRİTİK UYARI cümlesi döner.
     */
    @PostMapping(value = "/api/inspect", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public InspectResult inspect(@RequestParam("photo") MultipartFile photo) throws IOException {
        return images.inspect(bytes(photo));
    }

    /**
     * 4. ekran, senaryo B dosyasını üretir.
     * Gelen: notu 70 olan akıllı PNG.
     * İçeride: "puan":70 metnindeki '0' baytının son biti çevrilir, imza yenilenmez.
     * Dışarı: image/png, dosya adı sahte_vesikalik.png. Bu dosya inspect edilince puan 71 ve gecerli false okunur.
     */
    @PostMapping(value = "/api/tamper", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> tamper(@RequestParam("photo") MultipartFile photo) throws IOException {
        return png(images.tamperOneDigit(bytes(photo)), "sahte_vesikalik.png", 0, 0, 0);
    }

    /**
     * Yüklenen dosyanın baytlarını alır.
     * Gelen: MultipartFile, içerik PNG.
     * İçeride: getBytes(). Dosya boşsa "Fotoğraf seçilmedi." fırlatılır.
     * Dışarı: aynı bayt dizisi.
     */
    private static byte[] bytes(MultipartFile photo) throws IOException {
        if (photo == null || photo.isEmpty()) {
            throw new com.pixelbook.stego.StegoException("Fotoğraf seçilmedi.");
        }
        return photo.getBytes();
    }

    /**
     * PNG cevabını kurar.
     * Gelen: baytlar, ad "2026001_akilli_vesikalik.png", genişlik 320, yükseklik 400, jsonBytes 86.
     * İçeride: Content-Type image/png, Content-Disposition attachment, önbellek no-store.
     * Genişlik 0 ise ölçü başlığı konmaz.
     * Dışarı: HTTP 200 ve PNG gövdesi.
     */
    private static ResponseEntity<byte[]> png(byte[] body, String filename, int width, int height, int jsonBytes) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        headers.setCacheControl(CacheControl.noStore());
        if (width > 0) {
            headers.add("X-Pixelbook-Width", Integer.toString(width));
            headers.add("X-Pixelbook-Height", Integer.toString(height));
            headers.add("X-Pixelbook-Json-Bytes", Integer.toString(jsonBytes));
        }
        return ResponseEntity.ok().headers(headers).body(body);
    }

    /**
     * Ders ekleme gövdesi.
     * Gelen JSON: {"ders":"Veri Yapıları ve Algoritmalar","puan":85}.
     * Dışarı: ders() aynı metin, puan() 85.
     */
    public record CourseRequest(String ders, int puan) {
    }
}
