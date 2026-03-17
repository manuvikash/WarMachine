import { useState } from "react";
import { postSurvival } from "../api";
import SpeakButton from "./SpeakButton";

export default function SurvivalPanel({ onResult }) {
  const [question, setQuestion] = useState("");
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);

  const submit = async (e) => {
    e.preventDefault();
    if (!question.trim()) return;
    setLoading(true);
    setError(null);
    setResult(null);
    try {
      const data = await postSurvival(question.trim());
      setResult(data);
      onResult?.(question.trim(), data);
    } catch (err) {
      setError(err.message || "Request failed");
    } finally {
      setLoading(false);
    }
  };

  const speakText = result?.tts_summary || result?.answer || "";

  return (
    <div className="space-y-5">
      <div>
        <h2 className="text-xl font-semibold text-white">Survival RAG</h2>
        <p className="text-sm text-zinc-400 mt-1">
          Ask a survival question — answers grounded in war survival documents
        </p>
      </div>

      <form onSubmit={submit} className="space-y-3">
        <textarea
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          placeholder="e.g. What should I do during a nuclear attack?"
          rows={3}
          className="w-full rounded-lg bg-zinc-800 border border-zinc-700 focus:border-indigo-500 focus:outline-none px-4 py-3 text-sm text-zinc-200 placeholder:text-zinc-500 resize-none"
        />
        <button
          type="submit"
          disabled={!question.trim() || loading}
          className="w-full py-2.5 rounded-lg font-medium text-white bg-indigo-600 hover:bg-indigo-700 disabled:opacity-40 disabled:cursor-not-allowed transition-colors cursor-pointer"
        >
          {loading ? (
            <span className="inline-flex items-center gap-2">
              <svg className="animate-spin w-4 h-4" viewBox="0 0 24 24"><circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none"/><path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z"/></svg>
              Thinking…
            </span>
          ) : (
            "Ask"
          )}
        </button>
      </form>

      {error && (
        <div className="bg-red-900/40 border border-red-700 rounded-lg p-3 text-sm text-red-300">
          {error}
        </div>
      )}

      {result && (
        <div className="bg-zinc-800 rounded-xl p-5 space-y-4 text-left">
          <p className="text-sm text-zinc-200 leading-relaxed">{result.answer}</p>
          <SpeakButton text={speakText} />

          {result.sources?.length > 0 && (
            <div className="border-t border-zinc-700 pt-3 mt-3">
              <h4 className="text-xs font-semibold text-zinc-400 uppercase tracking-wide mb-2">Sources</h4>
              <ul className="space-y-1.5">
                {result.sources.map((s, i) => (
                  <li key={i} className="text-xs text-zinc-400">
                    <span className="text-indigo-400 font-medium">{s.source}</span>
                    <span className="ml-2 text-zinc-500">(score: {s.relevance_score})</span>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
