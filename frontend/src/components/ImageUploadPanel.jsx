import { useState, useRef } from "react";
import Markdown from "react-markdown";
import remarkGfm from "remark-gfm";
import SpeakButton from "./SpeakButton";

export default function ImageUploadPanel({ title, description, apiFn }) {
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
    } catch (err) {
      setError(err.message || "Request failed");
    } finally {
      setLoading(false);
    }
  };

  const speakText = result?.raw_llm_response || "";

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
        {preview ? (
          <img src={preview} alt="preview" className="mx-auto max-h-64 rounded-lg" />
        ) : (
          <div className="text-zinc-400">
            <svg className="mx-auto w-10 h-10 mb-2" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M12 16v-8m0 0l-3 3m3-3l3 3M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1" /></svg>
            <p className="text-sm">Drop an image here or click to upload</p>
            <p className="text-xs text-zinc-500 mt-1">JPEG, PNG, WebP</p>
          </div>
        )}
        <input
          ref={inputRef}
          type="file"
          accept="image/jpeg,image/png,image/webp"
          className="hidden"
          onChange={(e) => e.target.files[0] && handleFile(e.target.files[0])}
        />
      </div>

      {/* Submit */}
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

      {/* Error */}
      {error && (
        <div className="bg-red-900/40 border border-red-700 rounded-lg p-3 text-sm text-red-300">
          {error}
        </div>
      )}

      {/* Result */}
      {result && (
        <div className="bg-zinc-800 rounded-xl p-5 space-y-4 text-left">
          <div className="flex items-center justify-between">
            <h3 className="text-sm font-semibold text-zinc-300 uppercase tracking-wide">Result</h3>
            <SpeakButton text={speakText} />
          </div>
          <div className="prose prose-sm prose-invert max-w-none prose-headings:text-zinc-200 prose-strong:text-zinc-100 prose-li:text-zinc-300 prose-p:text-zinc-300">
            <Markdown remarkPlugins={[remarkGfm]}>{result.raw_llm_response}</Markdown>
          </div>
        </div>
      )}
    </div>
  );
}
