# Render Deploy Notes (Free Plan)

This repo now includes:
- `Dockerfile` for the Spring Boot app (`zynee-web`)
- `render.yaml` blueprint for:
  - `zynee-web` (Docker web service)
  - `zynee-assistant` (Python web service)

## What To Do In Render

1. Create or open a Blueprint in Render and point it to this repo (`main` branch).
2. Use `render.yaml` from repo root.
3. Sync/apply Blueprint.
4. In `zynee-web` service, set these secret env vars in Dashboard:
   - `SPRING_DATASOURCE_URL`
   - `SPRING_DATASOURCE_USERNAME`
   - `SPRING_DATASOURCE_PASSWORD`
   - `SPRING_MAIL_HOST`
   - `SPRING_MAIL_PORT`
   - `SPRING_MAIL_USERNAME`
   - `SPRING_MAIL_PASSWORD`
   - `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH`
   - `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE`
   - `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_REQUIRED`
5. In `zynee-assistant`, set `OLLAMA_URL` if you host Ollama outside Render.

## Important Free Plan Limits

- Free web services do not support persistent disks.
- Free web services can be restarted/spun down automatically.
- Free web services cannot send outbound traffic on SMTP ports `25`, `465`, `587`.
- Full Ollama hosting on free Render is typically not reliable because of resource and disk limits.

## Safe Recommendation

- Keep `zynee-web` and `zynee-assistant` on Render free.
- Host Ollama on a separate machine that can keep model files and memory stable.
