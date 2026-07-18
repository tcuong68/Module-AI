# GĐ2 — Dataset + Huấn luyện PhoBERT (SPEC §11, §13)

Chuẩn bị dataset và huấn luyện 2 mô hình PhoBERT cho tầng NLU: **intent classifier**
(8 nhãn) và **NER** (10 loại entity, BIO). Đây là bản pipeline cho **Giai đoạn 2** —
chưa bọc thành FastAPI service, chưa nối vào Spring Boot (đó là bước 2.3/2.4 của
SPEC, làm ở tăng vọt sau).

## ⚠️ Đọc trước — về bộ test "real"

`data/intent_test_real.jsonl` và `data/ner_test_real.jsonl` là bộ **PROXY tạm thời**
do người phát triển tự viết tay (đa dạng hơn dữ liệu synthetic: câu cụt, thiếu dấu,
viết tắt, teencode) — **KHÔNG PHẢI** dữ liệu thật thu thập từ Facebook/chotot như
SPEC §13.3 yêu cầu. Số liệu train/test dưới đây **chỉ để xác nhận pipeline chạy
đúng**, KHÔNG dùng để kết luận "mô hình đạt X% accuracy" khi bảo vệ chính thức —
cần thay bằng dữ liệu thật gán nhãn (Doccano/Label Studio) trước khi dùng số liệu
đánh giá này làm minh chứng. Xem cảnh báo chi tiết trong
`data/build_proxy_testset.py`.

## Cấu trúc

```
ml/
├── requirements.txt          # torch(cpu), transformers, datasets, evaluate, seqeval, py_vncorenlp
├── vncorenlp_util.py         # tải + bọc VnCoreNLP word-segmenter (tự viết, không dùng wget)
├── data/
│   ├── generate_dataset.py      # sinh intent_train.jsonl + ner_train.jsonl (synthetic, offset chính xác)
│   ├── build_proxy_testset.py   # sinh intent_test_real.jsonl + ner_test_real.jsonl (TAY VIẾT, xem cảnh báo trên)
│   ├── intent_train.jsonl       # 2500 câu, 8 intent
│   ├── intent_test_real.jsonl   # 142 câu (proxy)
│   ├── ner_train.jsonl          # 2075 câu có entity (offset ký tự)
│   └── ner_test_real.jsonl      # 87 câu có entity (proxy)
├── train_intent.py           # PhoBERT-base-v2 sequence classification (SPEC §11 bước 2.1)
└── train_ner.py               # PhoBERT-base-v2 token classification / BIO (SPEC §11 bước 2.2)
```

## Cài đặt

Cần Java (JDK) trên PATH/JAVA_HOME — VnCoreNLP chạy trên JVM qua `py_vncorenlp`/`pyjnius`
(máy này đã có JDK cho Spring Boot, ví dụ `~/.jdks/corretto-22.0.2`).

```bash
cd ml
python -m venv .venv
source .venv/Scripts/activate        # Windows Git Bash; PowerShell: .venv\Scripts\Activate.ps1
pip install -r requirements.txt

export JAVA_HOME="/duong/dan/toi/jdk"          # PowerShell: $env:JAVA_HOME = "..."
export PATH="$JAVA_HOME/bin:$PATH"
```

VnCoreNLP model (chỉ phần word-segmenter, không tải dep/ner/pos vì không cần) tự
tải lần đầu vào `ml/vncorenlp/` khi chạy script (qua `vncorenlp_util.py`, dùng
`urllib` thay vì `wget` — bản gốc của `py_vncorenlp.download_model()` gọi `wget`
qua `os.system()`, không có sẵn trên Windows).

## Chạy

```bash
# Sinh lại dataset (đã có sẵn trong data/, chỉ cần chạy lại nếu muốn thay đổi vocab/tỉ lệ)
python data/generate_dataset.py
python data/build_proxy_testset.py

# Smoke test (vài chục câu, 1 epoch) — xác nhận code chạy đúng trước khi train full
python train_intent.py --max-train 40 --epochs 1 --output-dir out-intent-smoke
python train_ner.py --max-train 40 --epochs 1 --output-dir out-ner-smoke

# Full training
python train_intent.py --epochs 5 --output-dir out-intent
python train_ner.py --epochs 8 --output-dir out-ner
```

Model + tokenizer được lưu vào `out-intent/`, `out-ner/` (đã gitignore — không commit
weight vào git).

## Kết quả đã đạt (chạy thật trên máy phát triển, CPU, không GPU)

Train xong cả 2 model (chạy song song trên CPU, ~24 phút mỗi model). Đánh giá trên
bộ **proxy** (`*_test_real.jsonl` — xem cảnh báo ở đầu file, KHÔNG phải dữ liệu
thật):

| Model | Epoch | Metric | Giá trị | Ngưỡng DoD (SPEC §1.3) |
|---|---|---|---|---|
| Intent (`out-intent/`) | 5 | Accuracy | 0.873 | DoD-1 ≥ 0.90 |
| Intent (`out-intent/`) | 5 | Macro-F1 | 0.875 | — |
| NER (`out-ner/`) | 8 | Precision / Recall | 0.742 / 0.714 | — |
| NER (`out-ner/`) | 8 | Entity-F1 (seqeval) | 0.728 | DoD-2 ≥ 0.85 |
| NER (`out-ner/`) | 8 | Token accuracy | 0.881 | — |

**Chưa đạt ngưỡng DoD** — dự kiến, vì 2 lý do biết trước:
1. Test set là **proxy tay viết** (142/87 câu), không phải phân phối thật của người
   dùng — SPEC §13.1 vốn đã lường trước train-synthetic/test-real sẽ lệch nhau,
   đây là lý do tồn tại của DoD chứ không phải bằng chứng model tệ.
2. NER F1 thấp hơn đáng kể so với accuracy (0.728 vs 0.881) — đặc trưng của
   seqeval: một entity dự đoán sai NHÃN hoặc LỆCH RANH GIỚI dù chỉ 1 token vẫn
   tính là sai toàn bộ span, trong khi accuracy tính theo từng token nên bị pha
   loãng bởi số lượng lớn token gắn nhãn "O".

**Việc cần làm để đạt DoD thật**: thay `*_test_real.jsonl` bằng dữ liệu thật thu
thập + gán nhãn tay (§13.3), rồi train lại — pipeline đã sẵn sàng, chỉ cần đổi
input.

Model đã lưu tại `out-intent/`, `out-ner/` (đã xóa checkpoint trung gian, chỉ giữ
model cuối — mỗi model ~515MB, đã gitignore, không commit vào git).

### Thời gian chạy (tham khảo)

~24 phút/model khi chạy song song trên CPU (batch 16). Nếu có GPU sẽ nhanh hơn
nhiều lần.

## Quyết định kỹ thuật đáng chú ý

- **Entity lưu theo offset ký tự** (kiểu Doccano/spaCy: `{"start","end","label"}`),
  không cố định BIO theo token ngay lúc sinh dữ liệu — vì ranh giới token phụ
  thuộc bộ word-segmenter, việc quy đổi sang BIO nên làm ở thời điểm train
  (`vncorenlp_util.segment_with_offsets` + `train_ner.py:assign_bio`).
- **Quy offset gốc → token đã segment**: VnCoreNLP wseg chỉ GHÉP các từ liền kề
  bằng `_`, không bao giờ tách nhỏ hơn hay đổi thứ tự — nên chỉ cần đếm số `_`
  trong mỗi token đã segment để biết nó "tiêu thụ" bao nhiêu từ gốc, từ đó suy
  ra lại offset trong văn bản gốc mà không cần thư viện alignment phức tạp.
- **PhoBERT không có tokenizer "Fast"** (không hỗ trợ `word_ids()`/`offset_mapping`).
  `train_ner.py` encode TỪNG TỪ đã segment riêng lẻ bằng tokenizer thường, gán
  nhãn cho subword đầu tiên của mỗi từ, `-100` cho phần còn lại — cách làm chuẩn
  khi không có fast tokenizer.
- **Nhiễu dữ liệu (~15%, §13.2)**: bỏ dấu toàn câu (`unicodedata` NFD + lọc
  combining mark) — phép biến đổi 1-đối-1 ký tự nên không làm lệch offset entity
  đã tính trước đó.
- **Interface `NluService` ở backend không đổi** (§9.1): khi model đã train xong
  và đạt yêu cầu, bước tiếp theo (2.3/2.4 — ngoài phạm vi phần này) là bọc model
  thành FastAPI service rồi cài `NluService` mới, không sửa tầng trên.

## Việc còn lại trước khi dùng cho bảo vệ chính thức

1. Thay `*_test_real.jsonl` bằng dữ liệu thật thu thập + gán nhãn tay.
2. ~~So sánh kết quả PhoBERT với đường cơ sở LLM (SPEC §14.4)~~ — đã đo đủ
   3 phương án 2026-07-18: harness `eval_nlu_compare.py`, bảng
   `eval-results/REPORT.md` (trên bộ proxy — có data thật thì đo lại).
3. ~~Bọc FastAPI (`nlu-service/`) + nối `PhoBertNluServiceImpl` vào Spring Boot
   (SPEC §11 bước 2.3/2.4)~~ — đã làm 2026-07-18, xem `nlu-service/README.md`.
