# RoomFinder Chat — Frontend (React + Vite + TS)

Chat widget tối giản gọi thẳng API `POST /api/v1/chat` của backend (xem `SPEC_Module_Chatbot_AI_Tim_Phong_Tro.md` §7, §10 bước 1.5). Không dùng UI framework/state-management ngoài — 1 hook (`useChat`) quản lý toàn bộ state hội thoại.

## Chạy

```bash
cd frontend
npm install
cp .env.example .env   # chỉnh VITE_API_BASE_URL nếu backend không chạy ở :8080
npm run dev
```

Mở `http://localhost:5173` (Vite tự đổi cổng nếu bận). Backend cần chạy sẵn (`mvn spring-boot:run` hoặc `docker compose up` ở thư mục gốc).

## Cấu trúc

```
src/
├── types/chat.ts      # TS type khớp đúng ChatRequest/ChatResponse/Filters/RoomCardDto/MetaDto (backend)
├── api/chatApi.ts      # sendMessage(), resetSession()
├── hooks/useChat.ts     # state hội thoại: messages, session_id (localStorage), loading
├── utils/filtersToText.ts  # dựng câu tìm kiếm tự nhiên từ active_filters (dùng khi xóa 1 chip)
├── components/
│   ├── ChatWidget.tsx   # khung chat chính
│   ├── MessageBubble.tsx
│   ├── RoomCard(List).tsx
│   ├── FilterChips.tsx  # hiển thị active_filters, nút "x" mỗi chip
│   └── MetaPanel.tsx    # panel gấp/mở: path/latency/confidence — phục vụ demo bảo vệ (§7.1, §15)
└── styles/app.css
```

## Quyết định thiết kế đáng chú ý

- **Xóa 1 filter qua chip**: backend không có API xóa riêng lẻ 1 tiêu chí (chỉ có RESET toàn bộ
  hoặc MERGE/OVERRIDE — §4.2 SPEC). Bấm "x" trên 1 chip sẽ **dựng lại câu tìm kiếm tự nhiên** từ
  các filter còn lại (`filtersToText.ts`) và gửi như tin nhắn chat bình thường, đúng tinh thần
  "gửi lệnh refine_search ngầm" mà SPEC §10 bước 1.5 mô tả — không cần sửa gì ở backend.
- **MetaPanel**: thay việc phải mở DevTools khi bảo vệ để chỉ ra `meta.path` (FAST/LLM/TEMPLATE/
  CLARIFY) — bấm để mở/thu gọn dưới mỗi câu trả lời của bot.
- **session_id**: sinh bằng `crypto.randomUUID()`, lưu `localStorage` để refresh trang không mất
  ngữ cảnh; nút "Cuộc trò chuyện mới" gọi `POST /api/v1/chat/reset` rồi sinh id mới.

## Đã kiểm thử

Chạy end-to-end qua Playwright (headless, tạm thời — không phải phần phụ thuộc lâu dài của dự án)
với backend thật (Docker, MySQL+Redis+Gemini): tìm phòng (FAST-path, 3 phòng) → refine "có điều
hòa nữa" (MERGE, còn 2 phòng, chip `air_conditioner` xuất hiện) → xóa chip → dựng lại câu tìm kiếm
đúng → hỏi so sánh → nhận bảng so sánh nhanh → "Cuộc trò chuyện mới" xóa sạch hội thoại. Không có
lỗi console.
