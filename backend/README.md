# Finoraax Universal Multi-Provider AI Backend

This is the standalone Python Flask AI backend for the Finoraax mobile & web application.

## Directory Structure
* `server.py`: Complete Flask application with multi-provider AI model fallback (Groq, Gemini, OpenAI, Claude, DeepSeek, Mistral, Ollama).
* `requirements.txt`: Python package dependencies.
* `.env`: Environment variables and API keys configuration.
* `vercel.json`: Vercel serverless deployment configuration.
* `Dockerfile`: Container image configuration for Google Cloud Run / Docker.
* `run_backend.bat`: Windows auto-restart script.

## Local Execution
```bash
python server.py
```
Or double-click `run_backend.bat` to keep the server running automatically.

## Deployment Options
* **Vercel CLI (No Git):** Navigate into `backend/` and run `npx vercel --prod`.
* **Render:** Connect repository and set start command to `gunicorn server:app`.
* **Google Cloud Run:** Run `gcloud run deploy --source .`.
