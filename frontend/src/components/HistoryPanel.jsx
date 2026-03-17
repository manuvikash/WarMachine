import SpeakButton from "./SpeakButton";

export default function HistoryPanel({ history }) {
  if (!history.length) {
    return (
      <div className="text-center py-16 text-zinc-500">
        <svg className="mx-auto w-10 h-10 mb-3 opacity-40" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
        <p className="text-sm">No history yet. Analyze an image or ask a question to get started.</p>
      </div>
    );
  }

  return (
    <div className="space-y-5">
      <h2 className="text-xl font-semibold text-white">History</h2>
      <div className="space-y-4">
        {history.map((entry) => (
          <div key={entry.id} className="bg-zinc-800 rounded-xl p-4 space-y-3">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-indigo-400 uppercase tracking-wide">
                {entry.type}
              </span>
              <span className="text-xs text-zinc-500">{entry.time}</span>
            </div>

            <div className="flex flex-col md:flex-row gap-4">
              {entry.image && (
                <img
                  src={entry.image}
                  alt="input"
                  className="rounded-lg max-h-40 object-contain md:w-1/4 shrink-0"
                />
              )}
              {entry.query && (
                <div className="bg-zinc-700/50 rounded-lg px-3 py-2 md:w-1/4 shrink-0">
                  <p className="text-xs text-zinc-400 mb-1">Question</p>
                  <p className="text-sm text-zinc-200">{entry.query}</p>
                </div>
              )}
              <div className="flex-1 space-y-2">
                <p className="text-sm text-zinc-200 leading-relaxed">{entry.response}</p>
                <SpeakButton text={entry.tts || entry.response} />
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
