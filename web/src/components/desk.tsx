"use client";

import { useEffect, useState } from "react";
import { EncodePanel } from "./encode-panel";
import { InspectPanel } from "./inspect-panel";
import { RegisterPanel } from "./register-panel";
import { RosterPanel } from "./roster-panel";
import { TranscriptPanel } from "./transcript-panel";

const SCREENS = [
  { id: "uret", index: "01", title: "Akıllı resim" },
  { id: "kayit", index: "02", title: "Hızlı kayıt" },
  { id: "karne", index: "03", title: "Transkript" },
  { id: "denetim", index: "04", title: "Denetim" },
  { id: "kayitlar", index: "05", title: "Kayıtlar" },
] as const;

type ScreenId = (typeof SCREENS)[number]["id"];

/**
 * Beş ekranı tek masada değiştirir.
 * Gelen: "kayit".
 * İçeride: görünür sayfa kayit olur, diğer paneller çizilmez.
 * Dışarı: ekranın ortasında hızlı kayıt durur.
 */
export function Desk() {
  const [screen, setScreen] = useState<ScreenId>("uret");
  const [burst, setBurst] = useState(0);

  useEffect(() => {
    if (!burst) return;
    const timer = window.setTimeout(() => setBurst(0), 1600);
    return () => window.clearTimeout(timer);
  }, [burst]);

  return (
    <div className="app">
      <div className="aurora" aria-hidden />
      <header className="topbar">
        <div className="logo">
          <i className="logo-dot" />
          <div>
            <strong>Pixelbook</strong>
            <span>Görsel transkript</span>
          </div>
        </div>
        <nav className="steps" aria-label="Ekranlar">
          {SCREENS.map((item) => (
            <button
              key={item.id}
              type="button"
              className={`step ${screen === item.id ? "active" : ""}`}
              aria-current={screen === item.id ? "page" : undefined}
              onClick={() => setScreen(item.id)}
            >
              <small>{item.index}</small>
              <b>{item.title}</b>
            </button>
          ))}
        </nav>
      </header>
      <main className="stage">
        <section className="sheet">
          {screen === "uret" ? <EncodePanel /> : null}
          {screen === "kayit" ? <RegisterPanel onBurst={() => setBurst((value) => value + 1)} /> : null}
          {screen === "karne" ? <TranscriptPanel active={screen === "karne"} onGoRegister={() => setScreen("kayit")} /> : null}
          {screen === "denetim" ? <InspectPanel /> : null}
          {screen === "kayitlar" ? <RosterPanel active={screen === "kayitlar"} /> : null}
        </section>
      </main>
      {burst > 0 ? <Confetti key={burst} /> : null}
    </div>
  );
}

/**
 * İlk kayıtta kısa bir konfeti dökümü yapar.
 * Gelen: burst sayısı artınca bileşen yeniden kurulur.
 * İçeride: 28 şerit, sırayla mavi ve nane, rastgele yatay konumda.
 * Dışarı: 1.5 saniyelik düşüş. Şeritler tıklamayı kesmez.
 */
function Confetti() {
  return (
    <div className="confetti" aria-hidden>
      {Array.from({ length: 28 }, (_, index) => (
        <i
          key={index}
          style={{
            left: `${(index * 37) % 100}%`,
            background: index % 2 === 0 ? "#6ea2ff" : "#3ee0b0",
            animationDelay: `${(index % 7) * 0.04}s`,
          }}
        />
      ))}
    </div>
  );
}
