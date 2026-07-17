import { useEffect, useRef, useState } from "react";
import { useChat } from "../hooks/useChat";
import { MessageBubble } from "./MessageBubble";

const SUGGESTIONS = [
  "Tìm phòng dưới 3 triệu ở Thanh Xuân",
  "Có phòng nào gần PTIT không?",
  "Tôi muốn phòng có điều hòa và chỗ để xe",
];

export function ChatWidget() {
  const { messages, loading, send, startNewConversation } = useChat();
  const [input, setInput] = useState("");
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" });
  }, [messages, loading]);

  const submit = (text: string) => {
    if (!text.trim()) return;
    void send(text);
    setInput("");
  };

  return (
    <div className="chat-widget">
      <header className="chat-widget__header">
        <span>RoomFinder Chat</span>
        <button type="button" onClick={() => void startNewConversation()}>
          Cuộc trò chuyện mới
        </button>
      </header>

      <div className="chat-widget__messages" ref={scrollRef}>
        {messages.length === 0 && (
          <div className="chat-widget__empty">
            <p>Hỏi mình về phòng trọ bạn đang tìm nhé. Ví dụ:</p>
            <div className="chat-widget__suggestions">
              {SUGGESTIONS.map((s) => (
                <button key={s} type="button" onClick={() => submit(s)}>
                  {s}
                </button>
              ))}
            </div>
          </div>
        )}
        {messages.map((m) => (
          <MessageBubble key={m.id} message={m} onRemoveFilter={submit} />
        ))}
        {loading && <div className="bubble bubble--bot bubble--pending">Đang trả lời…</div>}
      </div>

      <form
        className="chat-widget__input"
        onSubmit={(e) => {
          e.preventDefault();
          submit(input);
        }}
      >
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="Nhập tin nhắn..."
          disabled={loading}
        />
        <button type="submit" disabled={loading || !input.trim()}>
          Gửi
        </button>
      </form>
    </div>
  );
}
