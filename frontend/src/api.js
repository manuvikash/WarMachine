const API = import.meta.env.VITE_API_URL || "http://127.0.0.1:8000";

export async function postFirstAid(imageFile) {
  const form = new FormData();
  form.append("image", imageFile);
  const res = await fetch(`${API}/first-aid/`, { method: "POST", body: form });
  if (!res.ok) throw new Error(await res.text());
  return res.json();
}

export async function postHazard(imageFile) {
  const form = new FormData();
  form.append("image", imageFile);
  const res = await fetch(`${API}/hazard/`, { method: "POST", body: form });
  if (!res.ok) throw new Error(await res.text());
  return res.json();
}

export async function postSurvival(question) {
  const res = await fetch(`${API}/survival/`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ question }),
  });
  if (!res.ok) throw new Error(await res.text());
  return res.json();
}
