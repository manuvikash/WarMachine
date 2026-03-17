const ELEVENLABS_API_KEY = import.meta.env.VITE_ELEVENLABS_API_KEY;
const VOICE_ID = "JBFqnCBsd6RMkjVDRZzb"; // "George" — clear male narration voice

let currentAudio = null;
let objectUrl = null;

export async function speak(text) {
  stop();

  if (!ELEVENLABS_API_KEY) {
    throw new Error(
      "VITE_ELEVENLABS_API_KEY not set. Add it to your root .env file."
    );
  }

  const res = await fetch(
    `https://api.elevenlabs.io/v1/text-to-speech/${VOICE_ID}/stream`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "xi-api-key": ELEVENLABS_API_KEY,
      },
      body: JSON.stringify({
        text,
        model_id: "eleven_multilingual_v2",
        voice_settings: {
          stability: 0.5,
          similarity_boost: 0.75,
          style: 0.0,
          use_speaker_boost: true,
        },
      }),
    }
  );

  if (!res.ok) {
    const errBody = await res.text();
    console.error("ElevenLabs TTS error:", errBody);
    throw new Error(`ElevenLabs TTS failed: ${res.status}`);
  }

  const blob = await res.blob();
  objectUrl = URL.createObjectURL(blob);
  currentAudio = new Audio(objectUrl);

  return new Promise((resolve) => {
    currentAudio.onended = () => {
      _cleanup();
      resolve();
    };
    currentAudio.onerror = () => {
      _cleanup();
      resolve();
    };
    currentAudio.play();
  });
}

function _cleanup() {
  if (objectUrl) {
    URL.revokeObjectURL(objectUrl);
    objectUrl = null;
  }
  currentAudio = null;
}

export function stop() {
  if (currentAudio) {
    currentAudio.pause();
    _cleanup();
  }
}
