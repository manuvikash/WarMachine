import { useState, useEffect } from "react";
import ImageUploadPanel from "./components/ImageUploadPanel";
import SurvivalPanel from "./components/SurvivalPanel";
import HistoryPanel from "./components/HistoryPanel";
import { postFirstAid, postHazard, fetchHistory } from "./api";

const TABS = [
  { id: "first-aid", label: "First Aid" },
  { id: "hazard", label: "Hazard Detection" },
  { id: "survival", label: "Survival RAG" },
  { id: "history", label: "History" },
];

function App() {
  const [tab, setTab] = useState("first-aid");
  const [history, setHistory] = useState([]);

  useEffect(() => {
    fetchHistory().then(setHistory).catch(() => {});
  }, []);

  const addToHistory = (entry) => {
    const newEntry = {
      ...entry,
      id: Date.now(),
      time: new Date().toLocaleString(),
    };
    setHistory((prev) => [newEntry, ...prev]);
  };

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100">
      {/* Header */}
      <header className="border-b border-zinc-800 px-6 py-5">
        <div className="max-w-3xl mx-auto flex items-center gap-3">
          <div className="w-9 h-9 rounded-lg bg-indigo-600 flex items-center justify-center">
            <svg className="w-5 h-5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          </div>
          <div>
            <h1 className="text-lg font-bold tracking-tight">Emergency Response</h1>
            <p className="text-xs text-zinc-500">Powered by NVIDIA NIM</p>
          </div>
        </div>
      </header>

      {/* Tabs */}
      <nav className="border-b border-zinc-800">
        <div className="max-w-3xl mx-auto flex">
          {TABS.map((t) => (
            <button
              key={t.id}
              onClick={() => setTab(t.id)}
              className={`flex-1 py-3 text-sm font-medium transition-colors cursor-pointer ${
                tab === t.id
                  ? "text-indigo-400 border-b-2 border-indigo-500"
                  : "text-zinc-500 hover:text-zinc-300"
              }`}
            >
              {t.label}
              {t.id === "history" && history.length > 0 && (
                <span className="ml-1.5 text-[10px] bg-zinc-700 text-zinc-300 px-1.5 py-0.5 rounded-full">
                  {history.length}
                </span>
              )}
            </button>
          ))}
        </div>
      </nav>

      {/* Content */}
      <main className="max-w-3xl mx-auto px-6 py-8">
        {tab === "first-aid" && (
          <ImageUploadPanel
            title="First Aid Analysis"
            description="Upload an image of an injury to receive first-aid guidance"
            apiFn={postFirstAid}
            onResult={(image, data) =>
              addToHistory({ type: "First Aid", image, response: data.response, tts: data.tts_summary })
            }
          />
        )}
        {tab === "hazard" && (
          <ImageUploadPanel
            title="Hazard Detection"
            description="Upload an image of a scene to detect hazards and assess risk"
            apiFn={postHazard}
            onResult={(image, data) =>
              addToHistory({ type: "Hazard Detection", image, response: data.response, tts: data.tts_summary })
            }
          />
        )}
        {tab === "survival" && (
          <SurvivalPanel
            onResult={(query, data) =>
              addToHistory({ type: "Survival RAG", query, response: data.answer, tts: data.tts_summary })
            }
          />
        )}
        {tab === "history" && <HistoryPanel history={history} />}
      </main>
    </div>
  );
}

export default App;
