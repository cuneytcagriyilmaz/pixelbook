package com.pixelbook.web;

import com.pixelbook.Messages;
import com.pixelbook.stego.RgbImages;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OfficeFlowTest {

    @Autowired
    private MockMvc mvc;

    /**
     * Senaryonun dört ekranını HTTP üzerinden yürür.
     * Üretim Ahmet Yılmaz / 2026001 dosyasını indirir.
     * Kayıt formsuz duyuruyu döner.
     * Üç ders sırayla işlenir: 85, 70, 92.
     * Sahte kopyada ilk puan 85 iken 84 olur, mühür düşer, 70 ve 92 yerinde kalır.
     */
    @Test
    void walksTheFourScreens() throws Exception {
        byte[] portrait = RgbImages.samplePortrait("Ahmet Yılmaz");
        MockMultipartFile photo = new MockMultipartFile("photo", "ahmet.png", "image/png", portrait);
        MvcResult encoded = mvc.perform(multipart("/api/encode")
                        .file(photo)
                        .param("ad", "Ahmet Yılmaz")
                        .param("no", "2026001")
                        .param("bolum", "Bilgisayar Mühendisliği"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG))
                .andExpect(header().string("X-Pixelbook-Json-Bytes", org.hamcrest.Matchers.not("0")))
                .andReturn();
        byte[] smart = encoded.getResponse().getContentAsByteArray();

        mvc.perform(multipart("/api/register").file(new MockMultipartFile("photo", "smart.png", "image/png", smart)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mesaj").value(Messages.registered(
                        "Ahmet Yılmaz", "2026001", "Bilgisayar Mühendisliği")))
                .andExpect(jsonPath("$.yeniKayit").value(true));

        mvc.perform(multipart("/api/register").file(new MockMultipartFile("photo", "smart.png", "image/png", smart)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yeniKayit").value(false));

        add("Veri Yapıları ve Algoritmalar", 85);
        add("İşletim Sistemleri", 70);
        add("Ayrık Matematik", 92);

        mvc.perform(get("/api/students"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ogrenciler[?(@.no == '2026001')].dersler[1].ders").value(hasItem("İşletim Sistemleri")))
                .andExpect(jsonPath("$.ogrenciler[?(@.no == '2026001')].dersler[1].puan").value(hasItem(70)));

        byte[] stored = mvc.perform(get("/api/students/2026001/photo"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        mvc.perform(multipart("/api/inspect").file(new MockMultipartFile("photo", "smart.png", "image/png", stored)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gecerli").value(true))
                .andExpect(jsonPath("$.dersler[2].puan").value(92));

        byte[] forged = mvc.perform(multipart("/api/tamper")
                        .file(new MockMultipartFile("photo", "smart.png", "image/png", stored)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        mvc.perform(multipart("/api/inspect").file(new MockMultipartFile("photo", "sahte.png", "image/png", forged)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gecerli").value(false))
                .andExpect(jsonPath("$.mesaj").value(Messages.TAMPER))
                .andExpect(jsonPath("$.dersler[0].puan").value(84))
                .andExpect(jsonPath("$.dersler[1].puan").value(70))
                .andExpect(jsonPath("$.dersler[2].puan").value(92));

        mvc.perform(post("/api/students/2026001/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ders\":\"Taşkın\",\"puan\":150}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mesaj", containsString("150")));
    }

    /**
     * Kayıt defteri resimle yürür.
     * Elif 2026999 kaydı Veri Yapıları 85 taşır.
     * Vesikalık 2026998 ve ELİF DEMİR olarak yeniden mühürlenir, puan 85 kalır.
     * Başka öğrencinin numarasına yazmak 400 döner.
     * Puan 90 olur, satır silinir, vesikalık bırakılınca kayıt düşer.
     * Bozuk mühür satırı silmez.
     */
    @Test
    void managesRosterThroughPictures() throws Exception {
        byte[] elif = seal("Elif Demir", "2026999", "Bilgisayar Mühendisliği");
        registerFresh(elif);
        mvc.perform(post("/api/students/2026999/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ders\":\"Veri Yapıları ve Algoritmalar\",\"puan\":85}"))
                .andExpect(status().isOk());

        byte[] stored = photo("2026999");
        byte[] revised = mvc.perform(multipart("/api/revise")
                        .file(new MockMultipartFile("photo", "smart.png", "image/png", stored))
                        .param("ad", "ELİF DEMİR")
                        .param("no", "2026998")
                        .param("bolum", "Yazılım Mühendisliği"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG))
                .andExpect(header().string("Content-Disposition", containsString("2026998_akilli_vesikalik.png")))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        mvc.perform(multipart("/api/inspect").file(png(revised)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gecerli").value(true))
                .andExpect(jsonPath("$.ad").value("ELİF DEMİR"))
                .andExpect(jsonPath("$.no").value("2026998"))
                .andExpect(jsonPath("$.bolum").value("Yazılım Mühendisliği"))
                .andExpect(jsonPath("$.dersler[0].ders").value("Veri Yapıları ve Algoritmalar"))
                .andExpect(jsonPath("$.dersler[0].puan").value(85));

        mvc.perform(get("/api/students"))
                .andExpect(jsonPath("$.ogrenciler[?(@.no == '2026998')].ad").value(hasItem("ELİF DEMİR")))
                .andExpect(jsonPath("$.ogrenciler[?(@.no == '2026998')].dersler[0].puan").value(hasItem(85)))
                .andExpect(jsonPath("$.ogrenciler[?(@.no == '2026999')]").value(empty()));

        byte[] other = seal("Can Kaya", "2026997", "Bilgisayar Mühendisliği");
        registerFresh(other);
        mvc.perform(multipart("/api/revise")
                        .file(png(revised))
                        .param("ad", "ELİF DEMİR")
                        .param("no", "2026997")
                        .param("bolum", "Yazılım Mühendisliği"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mesaj", containsString("başka bir öğrenciye ait")));

        mvc.perform(put("/api/students/2026998/courses/0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ders\":\"Veri Yapıları ve Algoritmalar\",\"puan\":90}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mesaj").value(Messages.courseChanged("Veri Yapıları ve Algoritmalar", 90)))
                .andExpect(jsonPath("$.dersler[0].puan").value(90));

        mvc.perform(put("/api/students/2026998/courses/0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ders\":\"Taşkın\",\"puan\":150}"))
                .andExpect(status().isBadRequest());

        mvc.perform(delete("/api/students/2026998/courses/3"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mesaj", containsString("3 numaralı")));

        mvc.perform(delete("/api/students/2026998/courses/0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mesaj").value(Messages.courseRemoved("Veri Yapıları ve Algoritmalar")))
                .andExpect(jsonPath("$.dersler").value(empty()));

        byte[] forged = mvc.perform(multipart("/api/tamper").file(png(photo("2026997"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        mvc.perform(multipart("/api/remove").file(png(forged)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mesaj").value(Messages.TAMPER + " Kayıt silinmedi."));
        mvc.perform(get("/api/students"))
                .andExpect(jsonPath("$.ogrenciler[?(@.no == '2026997')].ad").value(hasItem("Can Kaya")));

        byte[] elifNow = photo("2026998");
        mvc.perform(multipart("/api/remove").file(png(elifNow)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mesaj").value(Messages.removed("ELİF DEMİR", "2026998")));
        mvc.perform(multipart("/api/remove").file(png(photo("2026997"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mesaj").value(Messages.removed("Can Kaya", "2026997")));
        mvc.perform(get("/api/students"))
                .andExpect(jsonPath("$.ogrenciler[?(@.no == '2026998')]").value(empty()))
                .andExpect(jsonPath("$.ogrenciler[?(@.no == '2026997')]").value(empty()));
    }

    /**
     * Kayıt defteri arar, dizer, sayfalar, kodla not günceller, çöpe atar ve geri alır.
     * Ada 2026881 ve Bora 2026882 vesikalıktan okunur.
     * k1 puanı 90 olur. İkisi de çöpe gider, Ada geri döner, sonra kalıcı silinir.
     */
    @Test
    void searchesSortsPagesAndUsesTheTrash() throws Exception {
        registerFresh(seal("Ada Yılmaz", "2026881", "Yazılım Mühendisliği"));
        registerFresh(seal("Bora Kaya", "2026882", "Bilgisayar Mühendisliği"));
        mvc.perform(post("/api/students/2026881/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ders\":\"Veri Yapıları ve Algoritmalar\",\"puan\":85}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dersler[0].kod").value("k1"))
                .andExpect(jsonPath("$.dersler[0].puan").value(85));

        mvc.perform(get("/api/students").param("q", "Ada"))
                .andExpect(jsonPath("$.toplam").value(1))
                .andExpect(jsonPath("$.ogrenciler[0].no").value("2026881"));

        mvc.perform(get("/api/students").param("q", "202688").param("bolum", "Bilgisayar Mühendisliği"))
                .andExpect(jsonPath("$.ogrenciler[0].no").value("2026882"));

        mvc.perform(get("/api/students").param("q", "202688").param("sirala", "ad").param("yon", "desc").param("boyut", "1"))
                .andExpect(jsonPath("$.toplam").value(2))
                .andExpect(jsonPath("$.ogrenciler.length()").value(1))
                .andExpect(jsonPath("$.ogrenciler[0].ad").value("Bora Kaya"));

        mvc.perform(get("/api/students/2026881"))
                .andExpect(jsonPath("$.ad").value("Ada Yılmaz"))
                .andExpect(jsonPath("$.cop").value(false))
                .andExpect(jsonPath("$.dersler[0].kod").value("k1"));

        mvc.perform(put("/api/students/2026881/notlar/k1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ders\":\"Veri Yapıları ve Algoritmalar\",\"puan\":90}"))
                .andExpect(jsonPath("$.dersler[0].kod").value("k1"))
                .andExpect(jsonPath("$.dersler[0].puan").value(90));

        mvc.perform(multipart("/api/remove-many")
                        .file(png(photo("2026881")))
                        .file(png(photo("2026882"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.silinen.length()").value(2));

        mvc.perform(get("/api/students").param("cop", "true").param("q", "202688"))
                .andExpect(jsonPath("$.toplam").value(2));

        mvc.perform(multipart("/api/restore").file(png(photo("2026881"))))
                .andExpect(jsonPath("$.mesaj").value(Messages.restored("Ada Yılmaz", "2026881")));

        mvc.perform(multipart("/api/remove").file(png(photo("2026881"))))
                .andExpect(status().isOk());
        mvc.perform(multipart("/api/purge").file(png(photo("2026881"))))
                .andExpect(jsonPath("$.mesaj").value(Messages.purged("Ada Yılmaz", "2026881")));
        mvc.perform(multipart("/api/purge").file(png(photo("2026882"))))
                .andExpect(status().isOk());

        mvc.perform(get("/api/students").param("q", "202688"))
                .andExpect(jsonPath("$.toplam").value(0));
        mvc.perform(get("/api/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].kullanici").value("sistem"));
    }

    private byte[] seal(String ad, String no, String bolum) throws Exception {
        byte[] portrait = RgbImages.samplePortrait(ad);
        return mvc.perform(multipart("/api/encode")
                        .file(new MockMultipartFile("photo", "portre.png", "image/png", portrait))
                        .param("ad", ad)
                        .param("no", no)
                        .param("bolum", bolum))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
    }

    private void registerFresh(byte[] smart) throws Exception {
        mvc.perform(multipart("/api/register").file(png(smart)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yeniKayit").value(true));
    }

    private byte[] photo(String no) throws Exception {
        return mvc.perform(get("/api/students/" + no + "/photo"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
    }

    private static MockMultipartFile png(byte[] body) {
        return new MockMultipartFile("photo", "smart.png", "image/png", body);
    }

    private void add(String ders, int puan) throws Exception {
        mvc.perform(post("/api/students/2026001/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ders\":\"" + ders + "\",\"puan\":" + puan + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mesaj").value(Messages.courseAdded(ders, puan)));
    }
}
