"use client";

import { useEffect, useState } from "react";
import {
  listAudit,
  listStudents,
  removeStudent,
  removeStudents,
  studentFile,
  type AuditRow,
  type ListQuery,
  type Student,
} from "@/lib/api";
import { Dropzone } from "./dropzone";
import { StudentDetail } from "./student-detail";

/**
 * Kayıt defteri. Bütün öğrencileri listeler. İsme basınca o öğrencinin dosyası açılır.
 * Gelen: kayıtlı no "2026001", resmin içinde Veri Yapıları 85.
 * İçeride: liste her PNG'yi çözer. openNo "2026001" olunca liste yerine dosya çizilir.
 * Dışarı: kartta ad, numara, bölüm ve not özeti durur. Dosyada tam tablo vardır.
 */
export function RosterPanel({ active }: { active: boolean }) {
  const [students, setStudents] = useState<Student[]>([]);
  const [toplam, setToplam] = useState(0);
  const [sayfa, setSayfa] = useState(1);
  const [boyut, setBoyut] = useState(8);
  const [bolumler, setBolumler] = useState<string[]>([]);
  const [draft, setDraft] = useState("");
  const [q, setQ] = useState("");
  const [bolum, setBolum] = useState("");
  const [sirala, setSirala] = useState("no");
  const [yon, setYon] = useState("asc");
  const [cop, setCop] = useState(false);
  const [picked, setPicked] = useState<string[]>([]);
  const [openNo, setOpenNo] = useState<string | null>(null);
  const [audit, setAudit] = useState<AuditRow[]>([]);
  const [stamp, setStamp] = useState(0);
  const [note, setNote] = useState("");
  const [bad, setBad] = useState(false);
  const [busy, setBusy] = useState(false);
  function query(extra: Partial<ListQuery> = {}): ListQuery {
    return { q, bolum, sirala, yon, sayfa, boyut, cop, ...extra };
  }

  async function load(extra: Partial<ListQuery> = {}) {
    const page = await listStudents(query(extra));
    setStudents(page.ogrenciler);
    setToplam(page.toplam);
    setSayfa(page.sayfa);
    setBoyut(page.boyut);
    setBolumler(page.bolumler);
    setStamp((value) => value + 1);
    setAudit(await listAudit().catch(() => []));
    return page;
  }

  useEffect(() => {
    if (!active) return;
    let cancelled = false;
    listStudents({ q, bolum, sirala, yon, sayfa, boyut, cop })
      .then((page) => {
        if (cancelled) return;
        setStudents(page.ogrenciler);
        setToplam(page.toplam);
        setSayfa(page.sayfa);
        setBoyut(page.boyut);
        setBolumler(page.bolumler);
      })
      .catch((error: Error) => {
        if (cancelled) return;
        setBad(true);
        setNote(error.message);
      });
    listAudit().then((rows) => {
      if (!cancelled) setAudit(rows);
    }).catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [active, q, bolum, sirala, yon, sayfa, boyut, cop]);

  async function run(work: () => Promise<string>) {
    setBusy(true);
    setBad(false);
    try {
      const message = await work();
      setNote(message);
      await load();
    } catch (error) {
      setBad(true);
      setNote(error instanceof Error ? error.message : "İstek tamamlanamadı.");
    } finally {
      setBusy(false);
    }
  }

  async function shred(file: File) {
    await run(async () => {
      const result = await removeStudent(file);
      return result.mesaj;
    });
  }

  async function shredPicked() {
    await run(async () => {
      const files = await Promise.all(picked.map((studentNo) => studentFile(studentNo)));
      const result = await removeStudents(files);
      setPicked([]);
      const gone = result.silinen.join(" ");
      const kept = result.reddedilen.join(" ");
      return [gone, kept].filter(Boolean).join(" ");
    });
  }

  if (openNo) {
    return <StudentDetail no={openNo} onBack={() => { setOpenNo(null); void load(); }} />;
  }

  return (
    <>
      <div className="sheet-top">
        <span>05 / Kayıtlar</span>
        <span>{toplam ? `${toplam} öğrenci` : "Boş"}</span>
      </div>
      <h1>Kayıt defteri</h1>
      <p className="lead">
        Bütün öğrenciler burada durur. İsme basınca o öğrencinin dosyası açılır. Notlar vesikalığın içinden okunur.
      </p>
      <div className="toolbar">
        <input
          className="search"
          value={draft}
          placeholder="Ad, numara, bölüm veya ders"
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Enter") {
              setSayfa(1);
              setQ(draft.trim());
            }
          }}
        />
        <button className="btn" type="button" onClick={() => { setSayfa(1); setQ(draft.trim()); }}>Ara</button>
        <select className="student-select" value={bolum} onChange={(event) => { setSayfa(1); setBolum(event.target.value); }}>
          <option value="">Bütün bölümler</option>
          {bolumler.map((item) => <option key={item} value={item}>{item}</option>)}
        </select>
        <select className="student-select" value={sirala} onChange={(event) => setSirala(event.target.value)}>
          <option value="no">Numara</option>
          <option value="ad">Ad</option>
          <option value="bolum">Bölüm</option>
        </select>
        <select className="student-select" value={yon} onChange={(event) => setYon(event.target.value)}>
          <option value="asc">Artan</option>
          <option value="desc">Azalan</option>
        </select>
        <button className="btn ghost" type="button" onClick={() => setCop((value) => { setSayfa(1); setPicked([]); return !value; })}>
          {cop ? "Deftere dön" : "Çöp kutusu"}
        </button>
        {picked.length ? (
          <button className="btn danger" type="button" disabled={busy} onClick={shredPicked}>
            Seçilen {picked.length} vesikalığı sil
          </button>
        ) : null}
      </div>
      {students.length ? (
        <div className="roster">
          {students.map((student) => {
            const photo = `/api/students/${encodeURIComponent(student.no)}/photo?t=${stamp}`;
            const grades = student.dersler.length
              ? student.dersler.map((course) => `${course.kod ? `${course.kod} ` : ""}${course.ders} ${course.puan}`).join(" · ")
              : "Henüz ders yok.";
            return (
              <article className="person" key={student.no}>
                <figure className="frame">
                  {/* Saklanan akıllı PNG. Optimize edici pikselleri yeniden kodlamasın. */}
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img src={photo} alt={`${student.ad} vesikalığı`} />
                </figure>
                <div>
                  <div className="person-head">
                    <h2>
                      {cop || !student.gecerli ? null : (
                        <input
                          className="pick"
                          type="checkbox"
                          checked={picked.includes(student.no)}
                          onChange={(event) => {
                            setPicked((current) => event.target.checked
                              ? [...current, student.no]
                              : current.filter((item) => item !== student.no));
                          }}
                        />
                      )}
                      <button className="name" type="button" onClick={() => setOpenNo(student.no)}>{student.ad}</button>
                    </h2>
                    <span className={student.gecerli ? "pill ok" : "pill bad"}>
                      {student.gecerli ? "Mühür geçerli" : "Mühür geçersiz"}
                    </span>
                  </div>
                  <p className="meta">{student.no} · {student.bolum}</p>
                  <p className="meta">{grades}</p>
                </div>
              </article>
            );
          })}
        </div>
      ) : (
        <p className="fine">Henüz kayıtlı öğrenci yok. Önce hızlı kayıt ekranına bir akıllı resim bırakın.</p>
      )}
      {toplam > boyut ? (
        <div className="pager">
          <button className="btn ghost" type="button" disabled={sayfa <= 1} onClick={() => setSayfa((value) => Math.max(1, value - 1))}>Önceki</button>
          <span>{sayfa} / {Math.max(1, Math.ceil(toplam / boyut))}</span>
          <button className="btn ghost" type="button" disabled={sayfa * boyut >= toplam} onClick={() => setSayfa((value) => value + 1)}>Sonraki</button>
        </div>
      ) : null}
      <p className={`note ${bad ? "bad" : ""}`}>{note}</p>
      {audit.length ? (
        <section className="audit">
          <h2 className="subhead">Kim ne değiştirdi</h2>
          <ul>
            {audit.map((row, index) => (
              <li key={`${row.zaman}-${index}`}>
                <b>{row.kullanici}</b>
                <span>{row.no}</span>
                <em>{row.ozet}</em>
              </li>
            ))}
          </ul>
        </section>
      ) : null}
      <Dropzone accept="image/png,image/*" onFile={shred} className="slim">
        <div>
          <strong>Silinecek vesikalığı buraya bırakın</strong>
          <br />
          <em>Mühür tutuyorsa kayıt silinir. Numara kutusu yoktur.</em>
        </div>
      </Dropzone>
    </>
  );
}
