# Pixelbook

Pixelbook, öğrenci işlerinin vesikalığı bir karnedir. Ad, numara, bölüm ve ders notları veritabanı kolonuna yazılmaz. Hepsi PNG dosyasının piksellerine gömülür ve HMAC-SHA256 ile mühürlenir. Dosya dışarıdan sıradan bir vesikalık gibi durur. Bir notun tek biti değişirse mühür düşer.

Bu depo o fikrin çalışan halidir: Java tarafı resmi üretir, okur ve saklar. Next.js tarafı öğrenci işleri masasını açar.

## Ne işe yarar

Görevli dört iş yapar.

1. Düz bir vesikalığa kimliği mühürler ve akıllı PNG'yi indirir.
2. Kayıt ekranında kutu doldurmaz. Yalnız dosyayı bırakır.
3. Ders notlarını aynı resmin içine, birer birer işler.
4. Şüpheli bir kopyayı denetime bırakır. Mühür tutuyorsa karne yeşil dökülür. Tutmuyorsa kırmızı alarm çıkar.

Kayıt defteri bütün öğrencileri listeler. İsme basınca o öğrencinin dosyası açılır. Silmek için numara yazılmaz. Vesikalık bırakılır, kayıt çöp kutusuna gider, oradan geri alınır ya da kalıcı silinir.

## Senaryo

Ahmet Yılmaz, `2026001`, Bilgisayar Mühendisliği.

Boş karne resmin içinde şudur:

```json
{"no":"2026001","ad":"Ahmet Yılmaz","bolum":"Bilgisayar Mühendisliği","dersler":[]}
```

Üç not işlenince liste büyür. Her satırın kalıcı bir kodu vardır. Sıra değişse de `k1` aynı satırdır.

```json
{"no":"2026001","ad":"Ahmet Yılmaz","bolum":"Bilgisayar Mühendisliği","dersler":[{"kod":"k1","ders":"Veri Yapıları ve Algoritmalar","puan":85},{"kod":"k2","ders":"İşletim Sistemleri","puan":70},{"kod":"k3","ders":"Ayrık Matematik","puan":92}]}
```

Ad, yazıldığı gibi kalır. `ALI` küçük harfe çevrilmez. Bölüm `CENG` kısaltmasına dönmez. `Bilgisayar Mühendisliği` nasıl yazıldıysa öyle mühürlenir.

İlk kayıt şu cümleyle biter:

```text
Ahmet Yılmaz (2026001) - Bilgisayar Mühendisliği kaydı başarıyla oluşturuldu!
```

İşletim Sistemleri notunun son rakamı bir bit kayarsa puan `70` iken `71` olur ve imza yenilenmez. Denetim ekranı şunu basar:

```text
KRİTİK UYARI: Görsel piksellerindeki ders notu verilerinde (payload) manipülasyon tespit edildi! Dijital imza geçersiz.
```

## Resmin içi

Gömülen şey düz JSON değildir. Önce bir çerçeveye kapanır.

```text
[ 4 bayt uzunluk ][ JSON, UTF-8 ][ 32 bayt HMAC-SHA256 ]
```

Uzunluk, JSON'un **bayt** sayısıdır ve big-endian yazılır. Karakter sayısı değildir. `ı` iki bayttır (`C4 B1`), `ü` iki bayttır (`C3 BC`). Okuyan taraf ilk dört baytı görünce nerede duracağını bilir. Ardından gelen 32 bayt imzadır.

İmza, `uzunluk || JSON` dizisinin HMAC-SHA256 sonucudur. Anahtar `application.yml` içindeki `pixelbook.hmac-secret` değeridir. Bu depodaki anahtar bir demo sırrıdır. Herkese açık bir sunucuda kullanılmaz.

Çerçeve, tuvalin piksellerine son bitten yazılır.

- Sıra satır satır, soldan sağa, yukarıdan aşağıdır.
- Her pikselde önce kırmızı, sonra yeşil, sonra mavi kanalın en düşük biti kullanılır.
- Her baytın yüksek biti önce gider.

`A` harfi `0x41`, yani `01000001` dir. Siyah bir piksele gömülünce ilk üç bit `0`, `1`, `0` olur. Yeşilin son biti `1` olunca o piksel `0xFF000100` olur. Göz bu farkı görmez. Okuma aynı sırayla yapılır ve `A` geri gelir.

JSON sığmazsa tuvalin altına beyaz satırlar eklenir ve çerçeve yeniden yazılır. Alfa kanalı beyaz zeminle birleştirilir. Böylece yarı saydam bir PNG, mühür sırasında kaymaz.

Mühürleme sırasında kodu boş olan ders satırına sıradaki `k1`, `k2` kodu verilir. Dolu kod korunur. Not güncellenince kod yerinde kalır, puan resmin içine yeniden yazılır, imza baştan hesaplanır.

## Veritabanı ne tutar

Kayıt, bellekteki bir H2 veritabanındadır. Adres `jdbc:h2:mem:pixelbook`. Sunucu kapanınca tablo boşalır. Diskte bir dosya yoktur.

`STUDENTS` tablosunda üç kolon vardır.

| Kolon | Anlamı |
| --- | --- |
| `ID` | Satırın kendi numarası |
| `SMART_PHOTO` | Akıllı vesikalığın baytları |
| `DELETED_AT` | Boşsa kayıt defterdedir. Doluysa çöp kutusundadır |

Numara, ad, bölüm ve ders notu bu tabloda kolon değildir. Hepsi `SMART_PHOTO` içindeki PNG'dedir. Liste bir öğrenciyi göstermek istediğinde o resmi açar ve metni piksellerden okur.

`AUDIT_EVENTS` tablosu resimde yapılan işlerin günlüğüdür. Kimlik ve notların kopyası değildir.

Aynı numara ikinci kez kayıt olursa yeni satır açılmaz. O numarayı taşıyan resmin kolonu yeni PNG ile değişir.

## Ekranlar

Arayüz `web/` altındadır. Beş masa vardır.

| | Ekran | Ne yapılır |
| --- | --- | --- |
| 01 | Akıllı resim | Vesikalık seçilir, kimlik yazılır, mühürlü PNG iner |
| 02 | Hızlı kayıt | Form yoktur. Yalnız akıllı resim bırakılır |
| 03 | Transkript | Seçilen öğrencinin resmine ders notu işlenir |
| 04 | Denetim | Resim açılır. Mühür yeşil ya da kırmızı döner |
| 05 | Kayıtlar | Bütün öğrenciler listelenir. İsme basınca dosya açılır |

Öğrenci dosyasında vesikalık, karne, not değiştirme, kimliği resme yeniden yazma ve silme durur. Silinen kayıt çöp kutusuna gider. Çöp kutusundan geri alınır ya da kalıcı silinir. Mührü bozuk bir resimle silme ve not değiştirme yapılmaz. Bozuk resmin içindeki numara yanlış öğrenciye ait olabilir.

Kayıt defterinde arama, bölüm süzgeci, Türkçe sıralama ve sayfalama vardır. Arama, kayıtlı adı küçültmez. Yalnız karşılaştırma kopyası Türkçe kurallarla katlanır. `Ç`, `C` den sonra gelir.

## Çalıştırmak

Java 17 veya daha yenisi, Maven ve Node.js gerekir.

Kayıt servisi, deponun kökünden:

```bash
mvn spring-boot:run
```

Servis `8080` portunu açar. H2 konsolu da buradadır: [http://localhost:8080/h2-console](http://localhost:8080/h2-console)

Konsol formu:

- JDBC URL: `jdbc:h2:mem:pixelbook;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE`
- Kullanıcı: `sa`
- Parola: boş

Arayüz:

```bash
cd web
npm install
npm run dev
```

Next.js hangi portu yazdıysa tarayıcı oradan açılır. Geliştirme sunucusu `/api` isteklerini `127.0.0.1:8080` adresine iletir. `3000` doluysa:

```bash
npm run dev -- -p 3001
```

Testler:

```bash
mvn test
```

Uygulama açıldığında tablolar boştur. Örnek öğrenci kendiliğinden yüklenmez. Ahmet'i görmek için 01. ekranda vesikalık üretip 02. ekrana bırakmak gerekir. Fotoğraf yoksa aynı ekrandaki örnek vesikalık, henüz mühürsüz düz bir kart çizer.

## HTTP uçları

Tarayıcı her işi resimle konuşur. Kimlik ve karne JSON gövdesiyle güncellenmez. Not eklemek bile saklanan PNG'yi açar, listeyi büyütür, yeniden mühürler ve `SMART_PHOTO` kolonunu o yeni baytlarla değiştirir.

| Metot | Yol | Gövde |
| --- | --- | --- |
| GET | `/api/sample-portrait` | Düz örnek vesikalık |
| POST | `/api/encode` | `photo`, `ad`, `no`, `bolum` |
| POST | `/api/register` | Yalnız `photo` |
| GET | `/api/students` | Sayfalı liste. Sorgu: `q`, `bolum`, `sirala`, `yon`, `sayfa`, `boyut`, `cop`, `hepsi` |
| GET | `/api/students/{no}` | Tek öğrenci, resimden okunur |
| GET | `/api/students/{no}/photo` | Saklanan PNG |
| POST | `/api/students/{no}/courses` | `{"ders","puan"}` |
| PUT | `/api/students/{no}/notlar/{kod}` | Kodlu not güncelleme |
| DELETE | `/api/students/{no}/notlar/{kod}` | Kodlu not silme |
| PUT | `/api/students/{no}/courses/{index}` | Sıra numarasıyla not güncelleme |
| DELETE | `/api/students/{no}/courses/{index}` | Sıra numarasıyla not silme |
| POST | `/api/revise` | `photo`, `ad`, `no`, `bolum`. Karne durur, kimlik resme yazılır |
| POST | `/api/remove` | Yalnız `photo`. Kayıt çöpe gider |
| POST | `/api/remove-many` | Aynı `photo` alanı birden çok kez |
| POST | `/api/restore` | Çöpteki vesikalık |
| POST | `/api/purge` | Çöpteki vesikalık kalıcı silinir |
| POST | `/api/inspect` | Mühür kontrolü |
| POST | `/api/tamper` | Demonun tek bit kaydırması. İmza yenilenmez |
| GET | `/api/audit` | Son resim işlemleri |

Liste bir dizi değildir. Şu zarf döner:

```json
{"ogrenciler":[],"toplam":0,"sayfa":1,"boyut":8,"bolumler":[]}
```

## Kod nerede

```text
src/main/java/com/pixelbook
├── stego          çerçeve, LSB, tuval, mühürleme
├── student        satır, vesikalığı saklama, arama, çöp
├── audit          resimde yapılan işlerin günlüğü
├── web            HTTP kapısı
└── config         HMAC ayarı
web/               Next.js masası
```

`stego` paketi resmin dilidir. `PayloadFrame` uzunluk ve imzayı kurar. `LsbChannel` bitleri yazar. `RgbImages` tuvali okur ve gerekirse uzatır. `SmartImageService` bunları tek mühürleme haline getirir.

`student` paketi ofistir. Satırı bulmak için numara kolonuna bakmaz. Her vesikalığı açar, içindeki `no` alanını okur.

## Sınırlar

Bu bir demo. H2 bellektedir. Sunucu yeniden başlayınca kayıtlar gider. HMAC anahtarı deponun içindedir. Üretimde bu anahtar başka yerde durur ve konsol kapanır.

PNG yeniden kodlanırsa son bitler bozulabilir. JPEG'e çevirmek mühürü düşürür. Bu kasıtlıdır. Karne, dosyanın kendisidir. Dosya ezilirse karne de ezilir.
