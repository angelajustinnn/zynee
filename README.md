# Zyneé

Zyneé is a mental wellness web application with journaling, mood tracking, quick check-ins, insights, and an emotional assistant.

## Tech Stack
- Spring Boot (Java)
- Thymeleaf templates
- Python assistant microservice
- Local ML model artifacts

## Local Run
1. Start Spring Boot app:
```bash
./mvnw spring-boot:run
```
2. Start assistant microservice:
```bash
cd assistant-microservice
source .venv/bin/activate
python -m uvicorn app:app --host 127.0.0.1 --port 8001
```

## Notes
- Keep secrets in local-only config files (not committed).
- Large raw/processed datasets are intentionally ignored from git.
