# NLU service (SPEC §11 bước 2.3).
# Build context là BACKEND ROOT (không phải nlu-service/) vì cần COPY
# ml/vncorenlp_util.py + VnCoreNLP jar/model — xem docker-compose.yml:
#   build: { context: ., dockerfile: nlu-service/Dockerfile }
# Model PhoBERT (~515MB/cái) KHÔNG bake vào image — mount volume ./ml/out-*.
FROM python:3.12-slim

# VnCoreNLP chạy trên JVM (py_vncorenlp/pyjnius)
RUN apt-get update \
    && apt-get install -y --no-install-recommends default-jre-headless \
    && rm -rf /var/lib/apt/lists/*
ENV JAVA_HOME=/usr/lib/jvm/default-java

WORKDIR /app/nlu-service

COPY nlu-service/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Chỉ cần jar + wordsegmenter; các thư mục models/* rỗng còn lại do
# py_vncorenlp yêu cầu tồn tại (xem vncorenlp_util._download_wseg_only).
COPY ml/vncorenlp_util.py /app/ml/vncorenlp_util.py
COPY ml/vncorenlp/VnCoreNLP-1.2.jar /app/ml/vncorenlp/VnCoreNLP-1.2.jar
COPY ml/vncorenlp/models/wordsegmenter /app/ml/vncorenlp/models/wordsegmenter
RUN mkdir -p /app/ml/vncorenlp/models/dep /app/ml/vncorenlp/models/ner \
             /app/ml/vncorenlp/models/postagger

COPY nlu-service/app.py .

EXPOSE 8000
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
