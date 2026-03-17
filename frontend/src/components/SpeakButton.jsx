import { useState } from "react";
import { speak, stop } from "../tts";

export default function SpeakButton({ text }) {
  const [playing, setPlaying] = useState(false);
  const [error, setError] = useState(null);

  if (!text) return null;

  const toggle = async () => {
    if (playing) {
      stop();
      setPlaying(false);
    } else {
      setPlaying(true);
      setError(null);
      try {
        await speak(text);
      } catch (err) {
        setError("TTS failed");
      } finally {
        setPlaying(false);
      }
    }
  };

  return (
    <div className="inline-flex items-center gap-2">
      <button
        onClick={toggle}
        className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium bg-indigo-600 hover:bg-indigo-700 text-white transition-colors cursor-pointer"
      >
        {playing ? (
          <>
            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor"><rect x="6" y="4" width="4" height="16" rx="1" fill="currentColor"/><rect x="14" y="4" width="4" height="16" rx="1" fill="currentColor"/></svg>
            Stop
          </>
        ) : (
          <>
            <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 24 24"><path d="M11.383 3.07C11.009 2.92 10.579 3.006 10.293 3.293L6.586 7H4a1 1 0 00-1 1v8a1 1 0 001 1h2.586l3.707 3.707A1 1 0 0012 20V4a1 1 0 00-.617-.93zM16.6 7.4a1 1 0 011.4 1.4 5.002 5.002 0 010 6.4 1 1 0 01-1.4-1.4 3.002 3.002 0 000-4z"/></svg>
            Read Aloud
          </>
        )}
      </button>
      {error && <span className="text-xs text-red-400">{error}</span>}
    </div>
  );
}
