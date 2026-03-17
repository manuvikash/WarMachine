import { useState, useRef } from "react";
import SpeakButton from "./SpeakButton";

export default function ImageUploadPanel({ title, description, apiFn, onResult }) {
  const [file, setFile] = useState(null);
  const [preview, setPreview] = useState(null);
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);
  const inputRef = useRef();

  const handleFile = (f) => {
    setFile(f);
    setPreview(URL.createObjectURL(f));
    setResult(null);
    setError(null);
  };

  const onDrop = (e) => {
    e.preventDefault();
    const f = e.dataTransfer.files[0];
    if (f) handleFile(f);
  };

  const submit = async () => {
    if (!file) return;
    setLoading(true);
    setError(null);
    setResult(null);
    try {
      const data = await apiFn(file);
      setResult(data);
      onResult?.(preview, data);
    } catch (err) {
      setError(err.message || "Request failed");
    } finally {
      setLoading(false);
    }
  };

  const speakText = result?.tts_summary || result?.response || "";

  return (
    <div className="space-y-5">
      <div>
        <h2 className="text-xl font-semibold text-white">{title}</h2>
        <p className="text-sm text-zinc-400 mt-1">{description}</p>
      </div>

      {/* Drop zone */}
      <div
        onDrop={onDrop}
        onDragOver={(e) => e.preventDefault()}
        onClick={() => inputRef.current?.click()}
        className="border-2 border-dashed border-zinc-600 hover:border-indigo-500 rounded-xl p-8 text-center cursor-pointer transition-colors"
      >
        {preview && !result ? (
          <img src={preview} alt="preview" className="mx-auto max-h-64 rounded-lg" />
        ) : !preview ? (
          <div className="text-zinc-400">
            <svg className="mx-auto w-10 h-10 mb-2" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M12 16v-8m0 0l-3 3m3-3l3 3M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1" /></svg>
            <p className="text-sm">Drop an image here or click to upload</p>
            <p className="text-xs text-zinc-500 mt-1">JPEG, PNG, WebP</p>
          </div>
        ) : null}
        <input
          ref={inputRef}
          type="file"
          accept="image/jpeg,image/png,image/webp"
          className="hidden"
          onChange={(e) => e.target.files[0] && handleFile(e.target.files[0])}
        />
      </div>

      {/* Submit */}
      {!result && (
        <button
          onClick={submit}
          disabled={!file || loading}
          className="w-full py-2.5 rounded-lg font-medium text-white bg-indigo-600 hover:bg-indigo-700 disabled:opacity-40 disabled:cursor-not-allowed transition-colors cursor-pointer"
        >
          {loading ? (
            <span className="inline-flex items-center gap-2">
              <svg className="animate-spin w-4 h-4" viewBox="0 0 24 24"><circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none"/><path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z"/></svg>
              Analyzing…
            </span>
          ) : (
            "Analyze Image"
          )}
        </button>
      )}

      {/* Error */}
      {error && (
        <div className="bg-red-900/40 border border-red-700 rounded-lg p-3 text-sm text-red-300">
          {error}
        </div>
      )}

      {/* Result — image + response side by side */}
      {result && (
        <div className="bg-zinc-800 rounded-xl p-5 space-y-4">
          <div className="flex flex-col md:flex-row gap-5">
            {preview && (
              <img src={preview} alt="uploaded" className="rounded-lg max-h-56 object-contain md:w-1/3 shrink-0" />
            )}
            <div className="flex-1 space-y-3">
              <p className="text-sm text-zinc-200 leading-relaxed">{result.response}</p>
              <SpeakButton text={speakText} />
            </div>
          </div>
          <button
            onClick={() => { setResult(null); setFile(null); setPreview(null); }}
            className="text-xs text-zinc-500 hover:text-zinc-300 transition-colors cursor-pointer"
          >
            ← Analyze another image
          </button>
        </div>
      )}
    </div>
  );
}
