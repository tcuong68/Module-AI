# MEMO: XÂY DỰNG CHATBOT AI HỖ TRỢ TÌM PHÒNG TRỌ

## 1. Mục tiêu

Xây dựng chatbot AI hỗ trợ người dùng tìm phòng trọ bằng ngôn ngữ tự nhiên, thay thế việc tìm kiếm bằng form truyền thống.

### Ví dụ

**Người dùng**
- "Tìm phòng dưới 3 triệu"
- "Có phòng nào gần PTIT không?"
- "Tôi muốn phòng có điều hòa và chỗ để xe"

**Chatbot**
- Hiểu yêu cầu
- Truy xuất dữ liệu hệ thống
- Gợi ý phòng phù hợp
- Hỗ trợ đặt lịch xem phòng

## 2. Kiến trúc đề xuất

```text
User
↓
Chatbot UI (React)
↓
Spring Boot Chat Service
↓
Intent & Entity Extraction
↓
Context Manager (Redis)
↓
Room Search Service
↓
Recommendation Engine (KNN)
↓
RAG + LLM
↓
Response
```

## 3. Các thành phần cần xây dựng

### 3.1 Intent Classification
**Mục tiêu:** Xác định mục đích người dùng.

**Intent dự kiến**
- search_room
- compare_room
- recommendation
- book_appointment
- room_detail
- contract_support
- payment_support

**Ví dụ**
```json
{"intent":"search_room"}
```

### 3.2 Entity Extraction

**Entity**
- price_min
- price_max
- location
- area
- furniture
- utility
- room_type

```json
{
  "price_max":3000000,
  "location":"PTIT",
  "air_conditioner":true
}
```

### 3.3 Context Management

Redis lưu session chat.

```json
{
  "price_max":3000000,
  "location":"Thanh Xuân"
}
```

### 3.4 Room Search Service

```sql
SELECT *
FROM room
WHERE price <= 3000000
AND district = 'Thanh Xuân';
```

### 3.5 Recommendation Engine

**Thuật toán**
- Content-Based Filtering
- KNN

**Feature**
- Giá
- Diện tích
- Vị trí
- Điều hòa
- WiFi
- Bãi xe
- Nội thất

### 3.6 RAG Layer

```text
User Question
↓
Search Database
↓
Top K Rooms
↓
Prompt Construction
↓
LLM
↓
Response
```

Chỉ lấy **Top 3–5** phòng phù hợp.

### 3.7 LLM Integration

LLM dùng để:
- Tư vấn
- Giải thích
- Tóm tắt
- So sánh phòng

LLM **không trực tiếp truy vấn dữ liệu**, chỉ sử dụng dữ liệu hệ thống đã truy xuất.

## 4. Các mô hình AI/ML đề xuất

### Bắt buộc
- LLM (GPT hoặc Gemini)
- KNN Recommendation

### Nâng cao
- BERT Intent Classification
- BERT NER Entity Extraction

## 5. Dataset cần chuẩn bị

### Intent Dataset
Ví dụ:
- "Tìm phòng dưới 3 triệu" → `search_room`
- "Đặt lịch xem phòng" → `book_appointment`
- "Có phòng nào gần PTIT không?" → `search_room`

**Mục tiêu:** 1000–3000 câu.

### Entity Dataset

Ví dụ:
- price_max = 3000000
- location = PTIT

**Mục tiêu:** 500–2000 câu.

## 6. Use Case Chatbot

- UC01 - Tìm phòng
- UC02 - So sánh phòng
- UC03 - Gợi ý phòng phù hợp
- UC04 - Xem chi tiết phòng
- UC05 - Đặt lịch xem phòng
- UC06 - Hỏi hợp đồng
- UC07 - Hỏi thanh toán
- UC08 - Hỗ trợ người thuê

## 7. Công nghệ triển khai

- Frontend: ReactJS
- Backend: Spring Boot
- Database: MySQL
- Cache: Redis
- Search: ElasticSearch
- Recommendation: KNN
- AI: GPT hoặc Gemini
- Container: Docker
- Message Queue: Kafka

## 8. Mức độ ưu tiên

### Giai đoạn 1 (MVP)
- Chatbot
- Parse yêu cầu
- Search phòng
- Context
- RAG

### Giai đoạn 2
- Recommendation KNN
- So sánh phòng
- Đặt lịch qua chatbot

### Giai đoạn 3
- Intent Model
- Entity Extraction Model
- Fine-tune NLP

## 9. Kết quả mong muốn khi bảo vệ

Chatbot có khả năng:
- Hiểu ngôn ngữ tự nhiên
- Ghi nhớ hội thoại
- Truy xuất dữ liệu thật
- Gợi ý phòng phù hợp
- Hỗ trợ đặt lịch xem phòng
- Giải thích lý do đề xuất

**Kiến trúc Hybrid AI**

Machine Learning + Recommendation + RAG + LLM.
