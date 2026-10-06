export type Course = { kod?: string | null; ders: string; puan: number };

export type Student = {
  no: string;
  ad: string;
  bolum: string;
  dersler: Course[];
  gecerli: boolean;
  cop?: boolean;
};

export type StudentPage = {
  ogrenciler: Student[];
  toplam: number;
  sayfa: number;
  boyut: number;
  bolumler: string[];
};

export type AuditRow = {
  zaman: string;
  kullanici: string;
  islem: string;
  no: string;
  ozet: string;
};

export type BulkRemove = {
  silinen: string[];
  reddedilen: string[];
};

/**
 * Ekranın HTTP çağrısını yapar.
 * Gelen: GET /api/students.
 * İçeride: fetch aynı yolu çağırır.
 * Dışarı: Response. Kayıt yoksa gövde {"mesaj":"..."}.
 */
async function request(path: string, init?: RequestInit): Promise<Response> {
  return fetch(path, init);
}

export type RegisterResult = {
  mesaj: string;
  no: string;
  ad: string;
  bolum: string;
  yeniKayit: boolean;
};

export type CourseUpdate = {
  mesaj: string;
  dersler: Course[];
  genislik: number;
  yukseklik: number;
  jsonBayt: number;
};

export type InspectResult = {
  gecerli: boolean;
  mesaj: string;
  ad: string;
  no: string;
  bolum: string;
  dersler: Course[];
  json: string;
};

export type SealedFile = {
  blob: Blob;
  filename: string;
  width: string | null;
  height: string | null;
  jsonBytes: string | null;
};

/**
 * Hata gövdesindeki cümleyi alır.
 * Gelen: HTTP 400 ve {"mesaj":"Fotoğraf seçilmedi."}.
 * İçeride: JSON ayrılır, mesaj alanı okunur.
 * Dışarı: "Fotoğraf seçilmedi." Gövde JSON değilse "İstek tamamlanamadı."
 */
async function errorMessage(response: Response): Promise<string> {
  const body = await response.json().catch(() => ({} as { mesaj?: string }));
  return body.mesaj || "İstek tamamlanamadı.";
}

/**
 * Content-Disposition içinden dosya adını söker.
 * Gelen: attachment; filename="2026001_akilli_vesikalik.png"
 * İçeride: tırnakların arası alınır.
 * Dışarı: "2026001_akilli_vesikalik.png". Başlık yoksa fallback döner.
 */
function filenameFrom(response: Response, fallback: string): string {
  const header = response.headers.get("Content-Disposition") || "";
  const match = /filename="([^"]+)"/.exec(header);
  return match ? match[1] : fallback;
}

/**
 * PNG baytlarını tarayıcıya indirtir.
 * Gelen: blob ve "2026001_akilli_vesikalik.png".
 * İçeride: geçici bir blob adresi üretilir.
 * Dışarı: indirme bu adla başlar, adres sonra silinir.
 */
export function saveBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

/**
 * Örnek vesikalığı dosya nesnesine çevirir. Bu karede JSON yoktur.
 * Gelen: "Ahmet Yılmaz".
 * İçeride: GET /api/sample-portrait PNG döner.
 * Dışarı: File adı ornek_vesikalik.png, tipi image/png.
 */
export async function samplePortrait(ad: string): Promise<File> {
  const response = await request("/api/sample-portrait?ad=" + encodeURIComponent(ad || "ORNEK"));
  if (!response.ok) {
    throw new Error(await errorMessage(response));
  }
  const blob = await response.blob();
  return new File([blob], "ornek_vesikalik.png", { type: "image/png" });
}

/**
 * 1. ekran. Fotoğraf ve kimliği mühürlü PNG'ye çevirir.
 * Gelen: dosya, ad "Ahmet Yılmaz", no "2026001", bölüm "Bilgisayar Mühendisliği".
 * İçeride: multipart /api/encode. Sunucu JSON'u son bitlere yazar.
 * Dışarı: png blob, dosya adı 2026001_akilli_vesikalik.png, jsonBytes "86".
 */
export async function encodeImage(file: File, ad: string, no: string, bolum: string): Promise<SealedFile> {
  const body = new FormData();
  body.append("photo", file);
  body.append("ad", ad);
  body.append("no", no);
  body.append("bolum", bolum);
  const response = await request("/api/encode", { method: "POST", body });
  if (!response.ok) {
    throw new Error(await errorMessage(response));
  }
  return {
    blob: await response.blob(),
    filename: filenameFrom(response, "akilli_vesikalik.png"),
    width: response.headers.get("X-Pixelbook-Width"),
    height: response.headers.get("X-Pixelbook-Height"),
    jsonBytes: response.headers.get("X-Pixelbook-Json-Bytes"),
  };
}

/**
 * 2. ekran. Akıllı resmi formsuz kaydeder.
 * Gelen: yalnız PNG.
 * İçeride: POST /api/register. Kimlik piksellerden okunur.
 * Dışarı: mesaj "Ahmet Yılmaz (2026001) - Bilgisayar Mühendisliği kaydı başarıyla oluşturuldu!", yeniKayit true.
 */
export async function registerStudent(file: File): Promise<RegisterResult> {
  const body = new FormData();
  body.append("photo", file);
  const response = await request("/api/register", { method: "POST", body });
  const payload = (await response.json().catch(() => ({}))) as Partial<RegisterResult> & { mesaj?: string };
  if (!response.ok) {
    throw new Error(payload.mesaj || "İstek tamamlanamadı.");
  }
  return payload as RegisterResult;
}

export type ListQuery = {
  q?: string;
  bolum?: string;
  sirala?: string;
  yon?: string;
  sayfa?: number;
  boyut?: number;
  cop?: boolean;
  hepsi?: boolean;
};

/**
 * Kayıt defterini süzer.
 * Gelen: q "Yılmaz", sayfa 1, boyut 8, cop false.
 * İçeride: sorgu dizesi kurulur. Sunucu her PNG'yi açar.
 * Dışarı: {ogrenciler:[...], toplam:1, sayfa:1, boyut:8, bolumler:["Bilgisayar Mühendisliği"]}
 */
export async function listStudents(query: ListQuery = {}): Promise<StudentPage> {
  const params = new URLSearchParams();
  if (query.q) params.set("q", query.q);
  if (query.bolum) params.set("bolum", query.bolum);
  if (query.sirala) params.set("sirala", query.sirala);
  if (query.yon) params.set("yon", query.yon);
  if (query.sayfa) params.set("sayfa", String(query.sayfa));
  if (query.boyut) params.set("boyut", String(query.boyut));
  if (query.cop) params.set("cop", "true");
  if (query.hepsi) params.set("hepsi", "true");
  const response = await request("/api/students?" + params.toString());
  if (!response.ok) {
    throw new Error(await errorMessage(response));
  }
  return response.json();
}

/**
 * Transkriptin açılır listesi için herkesi ister.
 * Gelen: hepsi true.
 * İçeride: sayfa kesilmez.
 * Dışarı: [{no:"2026001", dersler:[...]}]. Kayıt yoksa [].
 */
export async function allStudents(): Promise<Student[]> {
  const page = await listStudents({ hepsi: true });
  return page.ogrenciler;
}

/**
 * Tek öğrencinin vesikalığını açar.
 * Gelen: no "2026001".
 * İçeride: GET /api/students/2026001.
 * Dışarı: ad "Ahmet Yılmaz", dersler resmin içinden, cop false.
 */
/**
 * Saklanan vesikalığı dosya nesnesine çevirir.
 * Gelen: no "2026001".
 * İçeride: GET /api/students/2026001/photo.
 * Dışarı: File adı 2026001_akilli_vesikalik.png, tipi image/png.
 */
export async function studentFile(no: string): Promise<File> {
  const response = await request("/api/students/" + encodeURIComponent(no) + "/photo");
  if (!response.ok) {
    throw new Error(await errorMessage(response));
  }
  const blob = await response.blob();
  return new File([blob], no + "_akilli_vesikalik.png", { type: "image/png" });
}

export async function getStudent(no: string): Promise<Student> {
  const response = await request("/api/students/" + encodeURIComponent(no));
  const payload = (await response.json().catch(() => ({}))) as Partial<Student> & { mesaj?: string };
  if (!response.ok) {
    throw new Error(payload.mesaj || "İstek tamamlanamadı.");
  }
  return payload as Student;
}

/**
 * Seçili vesikalıkları çöpe atar.
 * Gelen: iki PNG dosyası.
 * İçeride: aynı photo alanı iki kez gider. Numara yazılmaz.
 * Dışarı: silinen ["Ahmet Yılmaz (2026001) kaydı silindi."], reddedilen [].
 */
export async function removeStudents(files: File[]): Promise<BulkRemove> {
  const body = new FormData();
  for (const file of files) body.append("photo", file);
  const response = await request("/api/remove-many", { method: "POST", body });
  const payload = (await response.json().catch(() => ({}))) as Partial<BulkRemove> & { mesaj?: string };
  if (!response.ok) {
    throw new Error(payload.mesaj || "İstek tamamlanamadı.");
  }
  return payload as BulkRemove;
}

/**
 * Çöpteki vesikalığı deftere geri koyar.
 * Gelen: mühürlü PNG.
 * İçeride: POST /api/restore.
 * Dışarı: mesaj "Ahmet Yılmaz (2026001) kaydı çöp kutusundan çıkarıldı."
 */
export async function restoreStudent(file: File): Promise<RemoveResult> {
  const body = new FormData();
  body.append("photo", file);
  const response = await request("/api/restore", { method: "POST", body });
  const payload = (await response.json().catch(() => ({}))) as Partial<RemoveResult> & { mesaj?: string };
  if (!response.ok) {
    throw new Error(payload.mesaj || "İstek tamamlanamadı.");
  }
  return payload as RemoveResult;
}

/**
 * Çöpteki kaydı kalıcı siler.
 * Gelen: mühürlü PNG.
 * İçeride: POST /api/purge.
 * Dışarı: mesaj "... kalıcı olarak silindi."
 */
export async function purgeStudent(file: File): Promise<RemoveResult> {
  const body = new FormData();
  body.append("photo", file);
  const response = await request("/api/purge", { method: "POST", body });
  const payload = (await response.json().catch(() => ({}))) as Partial<RemoveResult> & { mesaj?: string };
  if (!response.ok) {
    throw new Error(payload.mesaj || "İstek tamamlanamadı.");
  }
  return payload as RemoveResult;
}

/**
 * Son resim işlemlerini alır.
 * Gelen: kayıt ve not satırları birikmiş.
 * İçeride: GET /api/audit.
 * Dışarı: [{kullanici:"sistem", islem:"not-ekle", no:"2026001", ozet:"..."}]
 */
export async function listAudit(): Promise<AuditRow[]> {
  const response = await request("/api/audit");
  const payload = (await response.json().catch(() => ({}))) as { mesaj?: string };
  if (!response.ok) {
    throw new Error(payload.mesaj || "İstek tamamlanamadı.");
  }
  return payload as unknown as AuditRow[];
}

/**
 * Ders satırını koduyla günceller.
 * Gelen: no "2026001", kod "k1", ders "Veri Yapıları ve Algoritmalar", puan "90".
 * İçeride: PUT /api/students/2026001/notlar/k1. Kod k1 kalır.
 * Dışarı: mesaj "...notu 90 olarak güncellendi."
 */
export async function updateCourseByCode(no: string, kod: string, ders: string, puan: string): Promise<CourseUpdate> {
  const response = await request("/api/students/" + encodeURIComponent(no) + "/notlar/" + encodeURIComponent(kod), {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ ders, puan: Number(puan) }),
  });
  const payload = (await response.json().catch(() => ({}))) as Partial<CourseUpdate> & { mesaj?: string };
  if (!response.ok) {
    throw new Error(payload.mesaj || "İstek tamamlanamadı.");
  }
  return payload as CourseUpdate;
}

/**
 * Ders satırını koduyla siler.
 * Gelen: no "2026001", kod "k1".
 * İçeride: DELETE /api/students/2026001/notlar/k1.
 * Dışarı: mesaj "Veri Yapıları ve Algoritmalar notu karneiden silindi."
 */
export async function deleteCourseByCode(no: string, kod: string): Promise<CourseUpdate> {
  const response = await request("/api/students/" + encodeURIComponent(no) + "/notlar/" + encodeURIComponent(kod), {
    method: "DELETE",
  });
  const payload = (await response.json().catch(() => ({}))) as Partial<CourseUpdate> & { mesaj?: string };
  if (!response.ok) {
    throw new Error(payload.mesaj || "İstek tamamlanamadı.");
  }
  return payload as CourseUpdate;
}

/**
 * 3. ekran. Seçilen resme bir ders işler.
 * Gelen: no "2026001", ders "İşletim Sistemleri", puan "70".
 * İçeride: puan Number ile 70 olur. Gövde {"ders":"İşletim Sistemleri","puan":70}.
 * Dışarı: mesaj "...notu 70 olarak görsele işlendi.", jsonBayt 127.
 */
/**
 * Kayıt defteri. Vesikalığın kimliğini yeniler, karneyi PNG'nin içinde bırakır.
 * Gelen: dosya, eski resimde no "2026999" ve Veri Yapıları 85. Yeni ad "ELİF DEMİR", no "2026998", bölüm "Yazılım Mühendisliği".
 * İçeride: multipart /api/revise. Sunucu eski numarayı resmin içinden okur, yeni kimliği üstüne mühürler.
 * Dışarı: png blob, dosya adı 2026998_akilli_vesikalik.png, jsonBytes karneyi hâlâ taşır. "ELİF" küçülmez.
 */
export async function reviseImage(file: File, ad: string, no: string, bolum: string): Promise<SealedFile> {
  const body = new FormData();
  body.append("photo", file);
  body.append("ad", ad);
  body.append("no", no);
  body.append("bolum", bolum);
  const response = await request("/api/revise", { method: "POST", body });
  if (!response.ok) {
    throw new Error(await errorMessage(response));
  }
  return {
    blob: await response.blob(),
    filename: filenameFrom(response, "akilli_vesikalik.png"),
    width: response.headers.get("X-Pixelbook-Width"),
    height: response.headers.get("X-Pixelbook-Height"),
    jsonBytes: response.headers.get("X-Pixelbook-Json-Bytes"),
  };
}

export type RemoveResult = {
  mesaj: string;
  no: string;
  ad: string;
  bolum: string;
};

/**
 * Kayıt defteri. Silinecek kişiyi vesikalıktan okur.
 * Gelen: yalnız mühürlü PNG, içinde no "2026001".
 * İçeride: POST /api/remove. Numara yolu yoktur.
 * Dışarı: mesaj "Ahmet Yılmaz (2026001) kaydı silindi." Mühür bozuksa satır durur ve hata cümlesi döner.
 */
export async function removeStudent(file: File): Promise<RemoveResult> {
  const body = new FormData();
  body.append("photo", file);
  const response = await request("/api/remove", { method: "POST", body });
  const payload = (await response.json().catch(() => ({}))) as Partial<RemoveResult> & { mesaj?: string };
  if (!response.ok) {
    throw new Error(payload.mesaj || "İstek tamamlanamadı.");
  }
  return payload as RemoveResult;
}

/**
 * Kayıt defteri. Saklanan resmin bir ders satırını değiştirir.
 * Gelen: no "2026001", index 0, ders "Veri Yapıları ve Algoritmalar", puan "90".
 * İçeride: puan Number ile 90 olur. PUT gövdesi {"ders":"Veri Yapıları ve Algoritmalar","puan":90}.
 * Dışarı: mesaj "...notu 90 olarak güncellendi." Resmin kolonu yeni mühürlü PNG'dir.
 */
export async function updateCourse(no: string, index: number, ders: string, puan: string): Promise<CourseUpdate> {
  const response = await request("/api/students/" + encodeURIComponent(no) + "/courses/" + index, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ ders, puan: Number(puan) }),
  });
  const payload = (await response.json().catch(() => ({}))) as Partial<CourseUpdate> & { mesaj?: string };
  if (!response.ok) {
    throw new Error(payload.mesaj || "İstek tamamlanamadı.");
  }
  return payload as CourseUpdate;
}

/**
 * Kayıt defteri. Saklanan resmin bir ders satırını çıkarır.
 * Gelen: no "2026001", index 0. O satır "Veri Yapıları ve Algoritmalar".
 * İçeride: DELETE /api/students/2026001/courses/0. Sunucu satırı düşürüp PNG'yi yeniden mühürler.
 * Dışarı: mesaj "Veri Yapıları ve Algoritmalar notu karneiden silindi.", dersler [].
 */
export async function deleteCourse(no: string, index: number): Promise<CourseUpdate> {
  const response = await request("/api/students/" + encodeURIComponent(no) + "/courses/" + index, {
    method: "DELETE",
  });
  const payload = (await response.json().catch(() => ({}))) as Partial<CourseUpdate> & { mesaj?: string };
  if (!response.ok) {
    throw new Error(payload.mesaj || "İstek tamamlanamadı.");
  }
  return payload as CourseUpdate;
}

export async function addCourse(no: string, ders: string, puan: string): Promise<CourseUpdate> {
  const response = await request("/api/students/" + encodeURIComponent(no) + "/courses", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ ders, puan: Number(puan) }),
  });
  const payload = (await response.json().catch(() => ({}))) as Partial<CourseUpdate> & { mesaj?: string };
  if (!response.ok) {
    throw new Error(payload.mesaj || "İstek tamamlanamadı.");
  }
  return payload as CourseUpdate;
}

/**
 * 4. ekran. Vesikalığın mührünü sorar.
 * Gelen: PNG dosyası.
 * İçeride: POST /api/inspect.
 * Dışarı: gecerli true ve "İmza geçerli...". Rakamı bozulmuş kopyada gecerli false, puan 71, mesaj KRİTİK UYARI.
 */
export async function inspectImage(file: File): Promise<InspectResult> {
  const body = new FormData();
  body.append("photo", file);
  const response = await request("/api/inspect", { method: "POST", body });
  const payload = (await response.json().catch(() => ({}))) as Partial<InspectResult> & { mesaj?: string };
  if (!response.ok) {
    throw new Error(payload.mesaj || "İstek tamamlanamadı.");
  }
  return payload as InspectResult;
}

/**
 * 4. ekran. Bir rakamın son bitini çevirip indirilecek sahte kopyayı üretir.
 * Gelen: içinde "puan":70 olan PNG.
 * İçeride: POST /api/tamper. İmza yenilenmez. '0' baytı 0x30 iken 0x31 olur.
 * Dışarı: sahte_vesikalik.png blobu.
 */
export async function tamperImage(file: File): Promise<SealedFile> {
  const body = new FormData();
  body.append("photo", file);
  const response = await request("/api/tamper", { method: "POST", body });
  if (!response.ok) {
    throw new Error(await errorMessage(response));
  }
  return {
    blob: await response.blob(),
    filename: filenameFrom(response, "sahte_vesikalik.png"),
    width: null,
    height: null,
    jsonBytes: null,
  };
}
