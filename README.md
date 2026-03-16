# Emergency Response API

FastAPI backend with three endpoints for disaster/emergency scenarios, powered by NVIDIA NIM models.

| Endpoint | Input | Output | Model |
|---|---|---|---|
| `POST /first-aid/` | Image (JPEG/PNG/WebP) | First-aid guidance | `nvidia/cosmos-reason2-8b` |
| `POST /hazard/` | Image (JPEG/PNG/WebP) | Hazard detection & risk assessment | `nvidia/cosmos-reason2-8b` |
| `POST /survival/` | JSON `{ "question": "..." }` | RAG-grounded survival advice | `nvidia/nemotron-3-super-120b-a12b` |

## Quick Start

```bash
# 1. Clone & enter the project
cd nvidia

# 2. Create a virtual environment
python -m venv .venv
.venv\Scripts\activate      # Windows
# source .venv/bin/activate  # Linux/Mac

# 3. Install dependencies
pip install -r requirements.txt

# 4. Configure your API key
copy .env.example .env
# Edit .env and set NVIDIA_API_KEY

# 5. Run the server
uvicorn app.main:app --reload
```

The API docs will be at **http://127.0.0.1:8000/docs**.

## Project Structure

```
nvidia/
├── app/
│   ├── main.py                  # FastAPI app + lifespan
│   ├── config.py                # Pydantic settings (.env)
│   ├── models/
│   │   └── schemas.py           # Request/response models
│   ├── routers/
│   │   ├── first_aid.py         # POST /first-aid/
│   │   ├── hazard.py            # POST /hazard/
│   │   └── survival_rag.py      # POST /survival/
│   ├── services/
│   │   ├── nvidia_client.py     # OpenAI SDK → NVIDIA NIM
│   │   └── prompts.py           # System/user prompt templates
│   └── rag/
│       ├── vector_store.py      # ChromaDB ingest + query
│       └── documents/
│           └── survival_guide.txt
├── requirements.txt
├── .env.example
└── README.md
```

## Usage Examples

### First Aid (image upload)
```bash
curl -X POST http://127.0.0.1:8000/first-aid/ \
  -F "image=@injury_photo.jpg"
```

### Hazard Detection (image upload)
```bash
curl -X POST http://127.0.0.1:8000/hazard/ \
  -F "image=@disaster_scene.jpg"
```

### Survival RAG (text query)
```bash
curl -X POST http://127.0.0.1:8000/survival/ \
  -H "Content-Type: application/json" \
  -d '{"question": "How do I purify water in the wild?"}'
```

## Adding Survival Documents

Drop `.txt` or `.md` files into `app/rag/documents/`. They are automatically chunked and ingested into ChromaDB on server startup.
