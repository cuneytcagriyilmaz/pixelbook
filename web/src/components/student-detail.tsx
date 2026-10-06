"use client";

import { useEffect, useState } from "react";
import {
  deleteCourse,
  deleteCourseByCode,
  getStudent,
  listAudit,
  purgeStudent,
  removeStudent,
  restoreStudent,
  reviseImage,
  saveBlob,
  studentFile,
  updateCourse,
  updateCourseByCode,
  type AuditRow,
  type Student,
} from "@/lib/api";

/**
 * Bir öğrencinin dosyası. Liste bunu açar.
 * Gelen: no "2026001".
 * İçeride: vesikalık açılır, notlar resmin içinden okunur.
 * Dışarı: ad, numara, bölüm, mühür ve ders tablosu. Deftere dön listeye gider.
 */
export function StudentDetail({ no, onBack }: { no: string; onBack: () => void }) {
  const [studentNo, setStudentNo] = useState(no);
  const [student, setStudent] = useState<Student | null>(null);
  const [audit, setAudit] = useState<AuditRow[]>([]);
  const [stamp, setStamp] = useState(0);
  const [note, setNote] = useState("");
  const [bad, setBad] = useState(false);
  const [busy, setBusy] = useState(false);
  const [editing, setEditing] = useState(false);
  const [ad, setAd] = useState("");
  const [nextNo, setNextNo] = useState(no);
  const [department, setDepartment] = useState("");
  const [grade, setGrade] = useState<{ index: number; kod: string | null; ders: string; puan: string } | null>(null);
  const [confirm, setConfirm] = useState(false);

  useEffect(() => {
    let cancelled = false;
    getStudent(studentNo)
      .then((row) => {
        if (cancelled) return;
        setStudent(row);
      })
      .catch((error: Error) => {
        if (cancelled) return;
        setBad(true);
        setNote(error.message);
      });
    listAudit()
      .then((rows) => {
        if (!cancelled) setAudit(rows.filter((row) => row.no === studentNo));
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [studentNo, stamp]);

  async function run(work: () => Promise<string>, leave = false) {
    setBusy(true);
    setBad(false);
    try {
      const message = await work();
      if (leave) {
        onBack();
        return;
      }
      setNote(message);
      setStamp((value) => value + 1);
    } catch (error) {
      setBad(true);
      setNote(error instanceof Error ? error.message : "İstek tamamlanamadı.");
    } finally {
      setBusy(false);
    }
  }

  function startEdit() {
    if (!student) return;
    setEditing(true);
    setAd(student.ad);
    setNextNo(student.no);
    setDepartment(student.bolum);
    setGrade(null);
    setConfirm(false);
  }

  async function saveIdentity() {
    await run(async () => {
      const file = await studentFile(studentNo);
      const sealed = await reviseImage(file, ad, nextNo, department);
      saveBlob(sealed.blob, sealed.filename);
      const name = ad.trim();
      const renamed = nextNo.trim();
      const bolum = department.trim();
      setStudentNo(renamed);
      setEditing(false);
      return `${name} (${renamed}) - ${bolum} kaydı güncellendi. Ders notları resmin içinde duruyor. Tuval ${sealed.width}×${sealed.height}. JSON ${sealed.jsonBytes} bayt.`;
    });
  }

  async function saveGrade() {
    if (!grade) return;
    const current = grade;
    await run(async () => {
      const result = current.kod
        ? await updateCourseByCode(studentNo, current.kod, current.ders, current.puan)
        : await updateCourse(studentNo, current.index, current.ders, current.puan);
      setGrade(null);
      return `${result.mesaj} Tuval ${result.genislik}×${result.yukseklik}. JSON ${result.jsonBayt} bayt.`;
    });
  }

  async function dropGrade(index: number, kod: string | null) {
    await run(async () => {
      const result = kod ? await deleteCourseByCode(studentNo, kod) : await deleteCourse(studentNo, index);
      setGrade(null);
      return result.mesaj;
    });
  }

  if (!student) {
    return (
      <>
        <button className="btn ghost back" type="button" onClick={onBack}>Listeye dön</button>
        <p className={`note ${bad ? "bad" : ""}`}>{note || "Dosya açılıyor."}</p>
      </>
    );
  }

  const photo = `/api/students/${encodeURIComponent(student.no)}/photo?t=${stamp}`;
  const cop = Boolean(student.cop);

  return (
    <>
      <div className="sheet-top">
        <button className="mini" type="button" onClick={onBack}>Listeye dön</button>
        <span>{student.no}</span>
      </div>
      <h1>{student.ad}</h1>
      <p className="lead">
        Bu sayfa tek öğrencinin vesikalığını açar. Ad, bölüm ve notlar resmin içinden okunur.
      </p>
      <div className="dossier">
        <figure className="frame">
          {/* Saklanan akıllı PNG. Optimize edici pikselleri yeniden kodlamasın. */}
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src={photo} alt={`${student.ad} vesikalığı`} />
        </figure>
        <div>
          <div className="person-head">
            <p className="meta">{student.bolum}</p>
            <span className={student.gecerli ? "pill ok" : "pill bad"}>
              {student.gecerli ? "Mühür geçerli" : "Mühür geçersiz"}
            </span>
          </div>
          {student.gecerli ? null : (
            <p className="alarm">Mühür bozuk. Bu kayıt silinemez ve notu değiştirilemez.</p>
          )}
          <table className="ledger">
            <thead>
              <tr>
                <th>Ders</th>
                <th className="score">Puan</th>
                {student.gecerli && !cop ? <th /> : null}
              </tr>
            </thead>
            <tbody>
              {student.dersler.length ? (
                student.dersler.map((course, index) => (
                  <tr key={`${course.kod ?? course.ders}-${index}`}>
                    <td>{course.kod ? <span className="kod">{course.kod}</span> : null}{course.ders}</td>
                    <td className="score">{course.puan}</td>
                    {student.gecerli && !cop ? (
                      <td className="tools">
                        <button
                          className="mini"
                          type="button"
                          disabled={busy}
                          onClick={() => {
                            setGrade({ index, kod: course.kod ?? null, ders: course.ders, puan: String(course.puan) });
                            setEditing(false);
                          }}
                        >
                          Değiştir
                        </button>
                        <button className="mini warn" type="button" disabled={busy} onClick={() => dropGrade(index, course.kod ?? null)}>
                          Notu sil
                        </button>
                      </td>
                    ) : null}
                  </tr>
                ))
              ) : (
                <tr className="quiet">
                  <td colSpan={student.gecerli && !cop ? 3 : 2}>Henüz ders yok.</td>
                </tr>
              )}
            </tbody>
          </table>
          {grade && !cop ? (
            <form
              className="fields"
              onSubmit={(event) => {
                event.preventDefault();
                void saveGrade();
              }}
            >
              <label className="field">
                Ders
                <input value={grade.ders} onChange={(event) => setGrade({ ...grade, ders: event.target.value })} />
              </label>
              <label className="field">
                Puan
                <input
                  value={grade.puan}
                  inputMode="numeric"
                  onChange={(event) => setGrade({ ...grade, puan: event.target.value })}
                />
              </label>
              <div className="actions">
                <button className="btn" type="submit" disabled={busy}>Notu mühürle</button>
                <button className="btn ghost" type="button" onClick={() => setGrade(null)}>Vazgeç</button>
              </div>
            </form>
          ) : null}
          {editing && student.gecerli && !cop ? (
            <form
              className="fields"
              onSubmit={(event) => {
                event.preventDefault();
                void saveIdentity();
              }}
            >
              <label className="field">
                Ad soyad
                <input value={ad} onChange={(event) => setAd(event.target.value)} />
              </label>
              <label className="field">
                Öğrenci no
                <input value={nextNo} onChange={(event) => setNextNo(event.target.value)} />
              </label>
              <label className="field">
                Bölüm
                <input value={department} onChange={(event) => setDepartment(event.target.value)} />
              </label>
              <div className="actions">
                <button className="btn" type="submit" disabled={busy}>Kimliği resme yaz</button>
                <button className="btn ghost" type="button" onClick={() => setEditing(false)}>Vazgeç</button>
              </div>
            </form>
          ) : null}
          {cop ? (
            <div className="actions">
              <button className="btn ghost" type="button" disabled={busy} onClick={() => run(async () => (await restoreStudent(await studentFile(student.no))).mesaj, true)}>
                Geri al
              </button>
              <button className="btn danger" type="button" disabled={busy} onClick={() => run(async () => (await purgeStudent(await studentFile(student.no))).mesaj, true)}>
                Kalıcı sil
              </button>
            </div>
          ) : null}
          {student.gecerli && !cop ? (
            <div className="actions">
              {editing ? null : (
                <button className="btn ghost" type="button" disabled={busy} onClick={startEdit}>
                  Kimliği yenile
                </button>
              )}
              <a className="text-link quiet" href={photo} download={`${student.no}_akilli_vesikalik.png`}>
                Vesikalığı indir
              </a>
              {confirm ? (
                <button className="btn danger" type="button" disabled={busy} onClick={() => run(async () => (await removeStudent(await studentFile(student.no))).mesaj, true)}>
                  Silmeyi onayla
                </button>
              ) : (
                <button className="mini warn" type="button" disabled={busy} onClick={() => setConfirm(true)}>
                  Vesikalığıyla sil
                </button>
              )}
            </div>
          ) : null}
        </div>
      </div>
      <p className={`note ${bad ? "bad" : ""}`}>{note}</p>
      {audit.length ? (
        <section className="audit">
          <h2 className="subhead">Bu dosyada ne değişti</h2>
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
    </>
  );
}
