import { useState } from "react";
import ImageUploadPanel from "./components/ImageUploadPanel";
import SurvivalPanel from "./components/SurvivalPanel";
import { postFirstAid, postHazard } from "./api";

const TABS = [
  { id: "first-aid", label: "First Aid" },
  { id: "hazard", label: "Hazard Detection" },
  { id: "survival", label: "Survival RAG" },
];

function App() {
  const [tab, setTab] = useState("first-aid");

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
          />
        )}
        {tab === "hazard" && (
          <ImageUploadPanel
            title="Hazard Detection"
            description="Upload an image of a scene to detect hazards and assess risk"
            apiFn={postHazard}
          />
        )}
        {tab === "survival" && <SurvivalPanel />}
      </main>
    </div>
  );
}

export default App;
