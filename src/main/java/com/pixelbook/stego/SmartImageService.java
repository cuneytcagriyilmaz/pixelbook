package com.pixelbook.stego;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixelbook.Messages;
import com.pixelbook.config.PixelbookProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Görüntü işini tek kapıdan yürütür: mühürle, oku, ders ekle, kimliği yenile, ders satırını değiştir, satırı sil, bir rakamı boz.
 */
@Service
public class SmartImageService {

    private final ObjectMapper mapper;
    private final PayloadFrame frame;
    private final LsbChannel lsb = new LsbChannel();

    /**
     * Spring'in kurduğu hizmet.
     * Gelen: ObjectMapper ve pixelbook.hmac-secret = "pixelbook-demo-hmac-anahtari".
     * İçeride: sır UTF-8 baytlarına çevrilip PayloadFrame'e verilir.
     * Dışarı: encode, inspect, appendCourse, rewriteIdentity, replaceCourse, removeCourse ve tamperOneDigit çağrılmaya hazır nesne.
     */
    @Autowired
    public SmartImageService(ObjectMapper mapper, PixelbookProperties properties) {
        this(mapper, properties.hmacSecret());
    }

    /**
     * Testlerin kullandığı kurucu.
     * Gelen sır: "test-anahtar".
     * İçeride: getBytes(UTF-8) ile bayta çevrilir. "ALI" gelirse baytlar 41 4C 49 olur, küçülmez.
     * Dışarı: bu hizmet. Sır boşsa "HMAC anahtarı boş." fırlatılır.
     */
    public SmartImageService(ObjectMapper mapper, String hmacSecret) {
        if (hmacSecret == null || hmacSecret.isBlank()) {
            throw new StegoException("HMAC anahtarı boş.");
        }
        this.mapper = mapper;
        this.frame = new PayloadFrame(hmacSecret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 1. ekran. Vesikalığa kimliği gömer.
     * Gelen: PNG baytları, ad "Ahmet Yılmaz", no "2026001", bölüm "Bilgisayar Mühendisliği".
     * İçeride: dersler [] olan JSON kurulur, mühürlenir, sığmazsa tuval uzar, LSB'ye yazılır.
     * Dışarı: SealedImage. png akıllı vesikalık, jsonBytes gömülü metnin bayt uzunluğudur.
     */
    public SealedImage encode(byte[] photo, String ad, String no, String bolum) {
        TranscriptPayload payload = new TranscriptPayload(
                clean(no, "Öğrenci numarası"),
                clean(ad, "Ad soyad"),
                clean(bolum, "Bölüm"),
                List.of());
        return stamp(RgbImages.read(photo), payload);
    }

    /**
     * 3. ekran. Durmakta olan akıllı resme bir ders daha işler.
     * Gelen dersler [] ve ek "Veri Yapıları", puan 85.
     * İçeride liste [{"ders":"Veri Yapıları","puan":85}] olur, HMAC bu yeni JSON için yeniden hesaplanır.
     * Dışarı: yeni PNG. Eski resmin baytları değişmez; çağıran dönen diziyi saklar.
     * İkinci eklemede gelen liste tek derslidir, dışarı iki elemanlıdır. Puan 150 ise metot işlemez.
     */
    public SealedImage appendCourse(byte[] png, String ders, int puan) {
        String courseName = clean(ders, "Ders adı");
        int score = cleanScore(puan);
        OpenedCanvas opened = openTrusted(png);
        List<CourseGrade> next = new ArrayList<>(opened.payload().courses());
        next.add(new CourseGrade(courseName, score));
        return stamp(opened.rgb(), withCourses(opened.payload(), next));
    }

    /**
     * Kayıt defteri. Vesikalığın kimliğini yeniler, karneyi olduğu gibi bırakır.
     * Gelen: no "2026999", ad "Elif Demir", dersler [Veri Yapıları 85]. Yeni ad "ELİF DEMİR", yeni no "2026998", yeni bölüm "Yazılım Mühendisliği".
     * İçeride: mühür doğrulanır, ders listesi kopyalanır, kimlik alanları yenisiyle değişir, HMAC yeniden hesaplanır.
     * Dışarı: yeni PNG. İçindeki no "2026998", ad "ELİF DEMİR", puan hâlâ 85. "ELİF" küçülmez, bölüm "CENG" olmaz.
     * Mühür bozuksa resim yazılmaz.
     */
    public SealedImage rewriteIdentity(byte[] png, String ad, String no, String bolum) {
        OpenedCanvas opened = openTrusted(png);
        TranscriptPayload updated = new TranscriptPayload(
                clean(no, "Öğrenci numarası"),
                clean(ad, "Ad soyad"),
                clean(bolum, "Bölüm"),
                new ArrayList<>(opened.payload().courses()));
        return stamp(opened.rgb(), updated);
    }

    /**
     * Kayıt defteri. Seçilen ders satırının adını ve puanını değiştirir.
     * Gelen: liste [Veri Yapıları 85, İşletim Sistemleri 70], index 1, ders "İşletim Sistemleri", puan 75.
     * İçeride: 1. satırın yerine yeni satır konur, HMAC yeniden hesaplanır.
     * Dışarı: yeni PNG. İkinci puan 75, birinci puan 85. Index 5 ve karne 2 satırsa metot işlemez.
     */
    public SealedImage replaceCourse(byte[] png, int index, String ders, int puan) {
        String courseName = clean(ders, "Ders adı");
        int score = cleanScore(puan);
        OpenedCanvas opened = openTrusted(png);
        List<CourseGrade> next = new ArrayList<>(opened.payload().courses());
        requireIndex(next, index);
        next.set(index, new CourseGrade(next.get(index).kod(), courseName, score));
        return stamp(opened.rgb(), withCourses(opened.payload(), next));
    }

    /**
     * Kayıt defteri. Ders satırını koduyla değiştirir. Sıra kayarsa da aynı kod kalır.
     * Gelen: kod "k1", ders "Veri Yapıları ve Algoritmalar", puan 90. Eski puan 85.
     * İçeride: kodu k1 olan satır bulunur, puan 90 yapılır, kod k1 olarak kalır, HMAC yenilenir.
     * Dışarı: yeni PNG. k1 puanı 90. Kod yoksa "k9 kodlu ders satırı yok." fırlatılır.
     */
    public SealedImage replaceCourseByCode(byte[] png, String kod, String ders, int puan) {
        String courseName = clean(ders, "Ders adı");
        int score = cleanScore(puan);
        OpenedCanvas opened = openTrusted(png);
        List<CourseGrade> next = new ArrayList<>(assignCodes(opened.payload().courses()));
        int index = indexOfCode(next, kod);
        next.set(index, new CourseGrade(next.get(index).kod(), courseName, score));
        return stamp(opened.rgb(), withCourses(opened.payload(), next));
    }

    /**
     * Kayıt defteri. Ders satırını koduyla çıkarır.
     * Gelen: kod "k1", listede k1 Veri Yapıları 85 ve k2 İşletim Sistemleri 70.
     * İçeride: k1 düşer, k2 yerinde kalır, HMAC yenilenir.
     * Dışarı: yeni PNG. Dersler yalnız k2. Kod yoksa metot işlemez.
     */
    public SealedImage removeCourseByCode(byte[] png, String kod) {
        OpenedCanvas opened = openTrusted(png);
        List<CourseGrade> next = new ArrayList<>(assignCodes(opened.payload().courses()));
        int index = indexOfCode(next, kod);
        next.remove(index);
        return stamp(opened.rgb(), withCourses(opened.payload(), next));
    }

    /**
     * Kayıt defteri. Seçilen ders satırını karneden çıkarır.
     * Gelen: liste [Veri Yapıları 85, İşletim Sistemleri 70], index 0.
     * İçeride: ilk satır düşer, kalan listeyle HMAC yeniden hesaplanır.
     * Dışarı: yeni PNG. Dersler [İşletim Sistemleri 70]. Son satır da silinirse dersler [].
     */
    public SealedImage removeCourse(byte[] png, int index) {
        OpenedCanvas opened = openTrusted(png);
        List<CourseGrade> next = new ArrayList<>(opened.payload().courses());
        requireIndex(next, index);
        next.remove(index);
        return stamp(opened.rgb(), withCourses(opened.payload(), next));
    }

    /**
     * 2. ve 4. ekran. Resmi okur, mührü kontrol eder.
     * Gelen: encode ile üretilmiş PNG.
     * İçeride: ilk 4 bayt uzunluk, ardından JSON, ardından 32 bayt imza. İmza sır ile yeniden hesaplanır.
     * Dışarı: gecerli true, ad "Ahmet Yılmaz", json metin olarak aynı nesne.
     * Düz bir fotoğrafta uzunluk başlığı kapasiteye sığmazsa gecerli false ve UNREADABLE mesajı döner.
     */
    public InspectResult inspect(byte[] png) {
        try {
            BufferedImage rgb = RgbImages.read(png);
            PayloadFrame.Opened opened = openFrame(rgb);
            String json = new String(opened.json(), StandardCharsets.UTF_8);
            if (!opened.signatureValid()) {
                return untrusted(json);
            }
            TranscriptPayload payload = readPayload(opened.json());
            return new InspectResult(
                    true,
                    "İmza geçerli. Kimlik ve ders dökümü resmin piksellerinden okundu.",
                    payload.ad(),
                    payload.no(),
                    payload.bolum(),
                    List.copyOf(payload.courses()),
                    json);
        } catch (StegoException ex) {
            return new InspectResult(false, ex.getMessage(), "", "", "", List.of(), "");
        }
    }

    /**
     * 4. ekran, senaryo B. İmzayı yenilemeden bir rakamın son bitini çevirir.
     * Gelen JSON parçası "puan":70 ve karakter '0' = 0x30 = 00110000.
     * İçeride bu baytın son biti çevrilir: 00110001 = 0x31 = '1'. HMAC baytları yerinde kalır.
     * Dışarıdaki PNG okununca metin "puan":71 olur, imza "70" için üretildiği için geçersiz sayılır.
     * İki ders varsa ilk puanın son rakamı değişir. 85, 84 olur; sonraki 70 yerinde kalır.
     * Puan yoksa öğrenci numarasının ilk rakamı değişir. "2026001" içindeki '2' = 0x32, '3' = 0x33 olur.
     */
    public byte[] tamperOneDigit(byte[] png) {
        BufferedImage rgb = RgbImages.read(png);
        int jsonLength = jsonLength(rgb);
        byte[] json = lsb.extract(rgb, PayloadFrame.LENGTH_BYTES, jsonLength);
        int digitIndex = digitIndexToFlip(json);
        int bitIndex = (PayloadFrame.LENGTH_BYTES + digitIndex) * 8 + 7;
        lsb.flipBit(rgb, bitIndex);
        return RgbImages.toPng(rgb);
    }

    /**
     * Mührü tutan vesikalığı ve içindeki payload'ı birlikte verir.
     * Gelen: içinde no "2026001" olan mühürlü PNG.
     * İçeride: tuval okunur, çerçeve sökülür, imza yeniden hesaplanır, JSON nesneye çevrilir.
     * Dışarı: rgb tuval, payload.no() "2026001". İmza tutmuyorsa TAMPER fırlatılır, tuval döndürülmez.
     */
    private OpenedCanvas openTrusted(byte[] png) {
        BufferedImage rgb = RgbImages.read(png);
        PayloadFrame.Opened opened = openFrame(rgb);
        if (!opened.signatureValid()) {
            throw new StegoException(Messages.TAMPER);
        }
        return new OpenedCanvas(rgb, readPayload(opened.json()));
    }

    /**
     * Kimliği yerinde bırakıp ders listesini değiştirir.
     * Gelen: no "2026001", ad "Ahmet Yılmaz", dersler [Veri Yapıları 85].
     * İçeride: yeni payload aynı no, ad ve bölümle kurulur.
     * Dışarı: no() "2026001", courses() tek eleman. Ad "ALI" ise "ALI" kalır.
     */
    private static TranscriptPayload withCourses(TranscriptPayload current, List<CourseGrade> courses) {
        return new TranscriptPayload(current.no(), current.ad(), current.bolum(), courses);
    }

    /**
     * Ders satırının liste sınırında olup olmadığına bakar.
     * Gelen: liste 2 satır, index 1.
     * İçeride: 0 ve 1 kabul edilir.
     * Dışarı: index 1 için metot susar. Index 5 ise "5 numaralı ders satırı yok. Karne 2 satır." fırlatılır.
     */
    private static void requireIndex(List<CourseGrade> courses, int index) {
        if (index < 0 || index >= courses.size()) {
            throw new StegoException(index + " numaralı ders satırı yok. Karne " + courses.size() + " satır.");
        }
    }

    /**
     * Puanın 0 ile 100 arasında olduğunu zorlar.
     * Gelen: 85.
     * İçeride: sınır kontrolü.
     * Dışarı: 85. Gelen 150 ise "Puan 0 ile 100 arasında olmalı. Gelen: 150" fırlatılır.
     */
    private static int cleanScore(int puan) {
        if (puan < 0 || puan > 100) {
            throw new StegoException("Puan 0 ile 100 arasında olmalı. Gelen: " + puan);
        }
        return puan;
    }

    /**
     * Kodsuz ders satırlarına k1, k2 diye artan kod verir. Var olan kodu yerinde bırakır.
     * Gelen: [kodsuz Veri Yapıları 85, kodu k2 olan İşletim Sistemleri 70].
     * İçeride: k2 durur, kodsuz satır bir sonraki boş numarayı alır. Burada k1 dolu olmadığı için k3 değil, en yüksekten sonrası.
     * k2 varken kodsuz satır k3 olur.
     * Dışarı: her satırın kodu "k" ile başlar. Ders adı "ALI" ise "ALI" kalır.
     */
    static List<CourseGrade> assignCodes(List<CourseGrade> courses) {
        List<CourseGrade> source = courses == null ? List.of() : courses;
        Set<String> used = new HashSet<>();
        int highest = 0;
        for (CourseGrade course : source) {
            Integer number = codeNumber(course.kod());
            if (number != null) {
                used.add(course.kod());
                highest = Math.max(highest, number);
            }
        }
        List<CourseGrade> assigned = new ArrayList<>();
        for (CourseGrade course : source) {
            String kod = course.kod();
            boolean keep = kod != null && used.contains(kod) && assigned.stream().noneMatch(row -> kod.equals(row.kod()));
            if (keep) {
                assigned.add(course);
                continue;
            }
            String fresh;
            do {
                highest++;
                fresh = "k" + highest;
            } while (!used.add(fresh));
            assigned.add(new CourseGrade(fresh, course.ders(), course.puan()));
        }
        return assigned;
    }

    /**
     * "k12" içindeki 12'yi söker.
     * Gelen: "k1".
     * İçeride: k harfinden sonrası sayıya çevrilir.
     * Dışarı: 1. "ALI" ya da boş metin gelirse null.
     */
    private static Integer codeNumber(String kod) {
        if (kod == null || kod.length() < 2 || kod.charAt(0) != 'k') {
            return null;
        }
        try {
            return Integer.parseInt(kod.substring(1));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Kodun listedeki yerini bulur.
     * Gelen: liste [k1, k2], kod "k2".
     * İçeride: kod birebir aranır, küçültülmez.
     * Dışarı: 1. "k9" yoksa "k9 kodlu ders satırı yok." fırlatılır.
     */
    private static int indexOfCode(List<CourseGrade> courses, String kod) {
        String wanted = kod == null ? "" : kod.trim();
        for (int i = 0; i < courses.size(); i++) {
            if (wanted.equals(courses.get(i).kod())) {
                return i;
            }
        }
        throw new StegoException(wanted + " kodlu ders satırı yok.");
    }

    /**
     * JSON'u çerçeveye koyup tuvale yazar. Ders satırlarına kod verir.
     * Gelen payload no "2026001", ad "Ahmet Yılmaz", dersler [].
     * İçeride metin UTF-8 bayt olur. "Yılmaz" içindeki ı, metinde 1 karakter, baytta C4 B1 olarak 2 bayttır.
     * Uzunluk başlığı bu bayt sayısını yazar. Çerçeve sığmazsa expandIfNeeded alta beyaz satır ekler.
     * Dışarı: PNG baytları, tuvalin genişliği ve yüksekliği, JSON bayt uzunluğu.
     */
    private SealedImage stamp(BufferedImage rgb, TranscriptPayload payload) {
        TranscriptPayload coded = new TranscriptPayload(
                payload.no(), payload.ad(), payload.bolum(), assignCodes(payload.courses()));
        byte[] json = toJson(coded);
        byte[] sealed = frame.seal(json);
        BufferedImage canvas = RgbImages.expandIfNeeded(rgb, sealed.length);
        lsb.embed(canvas, sealed);
        return new SealedImage(RgbImages.toPng(canvas), canvas.getWidth(), canvas.getHeight(), json.length);
    }

    /**
     * Payload nesnesini kompakt UTF-8 JSON baytlarına çevirir.
     * Gelen: no 2026001, ad Ahmet Yılmaz, bölüm Bilgisayar Mühendisliği, dersler [].
     * İçeride: {"no":"2026001","ad":"Ahmet Yılmaz","bolum":"Bilgisayar Mühendisliği","dersler":[]}
     * Dışarı: bu metnin UTF-8 baytları. ü = C3 BC, ı = C4 B1. Bayt sayısı karakter sayısından büyüktür.
     */
    private byte[] toJson(TranscriptPayload payload) {
        try {
            return mapper.writeValueAsBytes(payload);
        } catch (JsonProcessingException ex) {
            throw new StegoException("JSON yazılamadı.", ex);
        }
    }

    /**
     * İmzası tutan JSON baytlarını nesneye çevirir.
     * Gelen baytlar {"no":"2026001","ad":"Ahmet Yılmaz","bolum":"Bilgisayar Mühendisliği","dersler":[]}.
     * İçeride alanlar no, ad, bolum, dersler sırasıyla doldurulur.
     * Dışarı: no() "2026001". no, ad veya bolum boşsa "Gömülü kayıtta kimlik alanı boş." fırlatılır.
     */
    private TranscriptPayload readPayload(byte[] json) {
        try {
            TranscriptPayload payload = mapper.readValue(json, TranscriptPayload.class);
            if (payload.no() == null || payload.no().isBlank()
                    || payload.ad() == null || payload.ad().isBlank()
                    || payload.bolum() == null || payload.bolum().isBlank()) {
                throw new StegoException("Gömülü kayıtta kimlik alanı boş.");
            }
            return payload;
        } catch (IOException ex) {
            throw new StegoException("Gömülü JSON okunamadı.", ex);
        }
    }

    /**
     * Tuvalden çerçeveyi söküp mührü açar.
     * Gelen: başına 43 bayt gömülmüş bir tuval.
     * İçeride: ilk 4 bayttan uzunluk okunur, çerçeve 4 + uzunluk + 32 bayt kesilir.
     * Dışarı: Opened. Uzunluk kapasiteden büyükse "Bu resimde okunabilir bir gömülü kayıt yok." fırlatılır.
     */
    private PayloadFrame.Opened openFrame(BufferedImage rgb) {
        byte[] sealed = lsb.extract(rgb, 0, jsonLength(rgb) + PayloadFrame.LENGTH_BYTES + PayloadFrame.HMAC_BYTES);
        return frame.open(sealed);
    }

    /**
     * Uzunluk başlığını okur ve çerçevenin tuvale sığıp sığmadığına bakar.
     * Gelen başlık [0, 0, 0, 13].
     * İçeride sayı 13 olur. Çerçeve 4 + 13 + 32 = 49 bayttır.
     * Dışarı: 13. Başlık kapasiteden büyük bir sayıysa UNREADABLE fırlatılır.
     */
    private int jsonLength(BufferedImage rgb) {
        int capacity = RgbImages.capacityBytes(rgb.getWidth(), rgb.getHeight());
        if (capacity < PayloadFrame.LENGTH_BYTES + PayloadFrame.HMAC_BYTES) {
            throw new StegoException(Messages.UNREADABLE);
        }
        byte[] lengthBytes = lsb.extract(rgb, 0, PayloadFrame.LENGTH_BYTES);
        int jsonLength = PayloadFrame.readLength(lengthBytes);
        int overhead = PayloadFrame.LENGTH_BYTES + PayloadFrame.HMAC_BYTES;
        if (jsonLength < 2 || jsonLength > capacity - overhead) {
            throw new StegoException(Messages.UNREADABLE);
        }
        return jsonLength;
    }

    /**
     * İmza tutmayan ama hâlâ JSON olan metni tabloya döker.
     * Gelen: {"no":"2026001",...,"dersler":[{"ders":"İşletim Sistemleri","puan":71}]}
     * İçeride nesneye çevrilir, gecerli false bırakılır.
     * Dışarı: mesaj TAMPER, puan 71. Metin JSON değilse alanlar boş, mesaj yine TAMPER, json ham metin kalır.
     */
    private InspectResult untrusted(String json) {
        try {
            TranscriptPayload payload = mapper.readValue(json.getBytes(StandardCharsets.UTF_8), TranscriptPayload.class);
            return new InspectResult(
                    false,
                    Messages.TAMPER,
                    payload.ad() == null ? "" : payload.ad(),
                    payload.no() == null ? "" : payload.no(),
                    payload.bolum() == null ? "" : payload.bolum(),
                    payload.courses(),
                    json);
        } catch (IOException ex) {
            return new InspectResult(false, Messages.TAMPER, "", "", "", List.of(), json);
        }
    }

    /**
     * Bozulacak rakamın JSON içindeki yerini seçer.
     * Gelen: ...{"ders":"İşletim Sistemleri","puan":70}...
     * İçeride "puan": işaretinden sonraki son rakamın indeksi alınır. 70 için bu '0' karakteridir.
     * Dışarı: o baytın indeksi. "puan": yoksa ilk rakamın indeksi. "2026001" için '2' nin yeri.
     * Rakam da yoksa 0, yani ilk bayt.
     */
    static int digitIndexToFlip(byte[] json) {
        byte[] marker = "\"puan\":".getBytes(StandardCharsets.US_ASCII);
        int markerAt = indexOf(json, marker);
        if (markerAt >= 0) {
            int start = markerAt + marker.length;
            int end = start;
            while (end < json.length && json[end] >= '0' && json[end] <= '9') {
                end++;
            }
            if (end > start) {
                return end - 1;
            }
        }
        for (int i = 0; i < json.length; i++) {
            if (json[i] >= '0' && json[i] <= '9') {
                return i;
            }
        }
        return 0;
    }

    /**
     * Bayt dizisinin içinde kısa bir diziyi arar.
     * Gelen: {"puan":70} baytları ve iğne "puan": baytları.
     * İçeride her başlangıç konumu karşılaştırılır.
     * Dışarı: "puan": ifadesinin başladığı indeks. Yoksa -1.
     */
    static int indexOf(byte[] data, byte[] needle) {
        outer:
        for (int i = 0; i <= data.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (data[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    /**
     * Form metninin iki ucundaki boşluğu keser.
     * Gelen: "  Ahmet Yılmaz  ".
     * İçeride: trim.
     * Dışarı: "Ahmet Yılmaz". "ALI" gelirse "ALI" kalır, harfler küçülmez. Boşsa "Ad soyad boş." gibi bir cümle fırlatılır.
     */
    private static String clean(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new StegoException(label + " boş.");
        }
        return value.trim();
    }

    /**
     * Mührü açılmış tuval ile içindeki kimlik ve karne.
     * Gelen: 320x400 tuval, payload no "2026001".
     * Dışarı: rgb() aynı tuval, payload().no() "2026001".
     */
    private record OpenedCanvas(BufferedImage rgb, TranscriptPayload payload) {
    }
}
