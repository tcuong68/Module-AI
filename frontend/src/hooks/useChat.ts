import { useCallback, useRef, useState } from "react";
import { resetSession, sendMessage } from "../api/chatApi";
import type { ChatMeta, Filters, RoomCard } from "../types/chat";

const SESSION_STORAGE_KEY = "roomfinder_chat_session_id";

export interface ChatMessage {
  id: string;
  role: "user" | "bot" | "error";
  text: string;
  rooms?: RoomCard[];
  activeFilters?: Filters;
  meta?: ChatMeta;
}

function newSessionId(): string {
  return `s-${crypto.randomUUID()}`;
}

function loadOrCreateSessionId(): string {
  const existing = localStorage.getItem(SESSION_STORAGE_KEY);
  if (existing) return existing;
  const id = newSessionId();
  localStorage.setItem(SESSION_STORAGE_KEY, id);
  return id;
}

export function useChat() {
  const [sessionId, setSessionId] = useState<string>(() => loadOrCreateSessionId());
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [loading, setLoading] = useState(false);
  const msgCounter = useRef(0);

  const nextId = () => `m-${++msgCounter.current}`;

  const send = useCallback(
    async (text: string) => {
      const trimmed = text.trim();
      if (!trimmed || loading) return;

      setMessages((prev) => [...prev, { id: nextId(), role: "user", text: trimmed }]);
      setLoading(true);
      try {
        const res = await sendMessage({ session_id: sessionId, message: trimmed });
        if (res.session_id && res.session_id !== sessionId) {
          setSessionId(res.session_id);
          localStorage.setItem(SESSION_STORAGE_KEY, res.session_id);
        }
        setMessages((prev) => [
          ...prev,
          {
            id: nextId(),
            role: "bot",
            text: res.reply,
            rooms: res.rooms,
            activeFilters: res.active_filters,
            meta: res.meta,
          },
        ]);
      } catch (err) {
        setMessages((prev) => [
          ...prev,
          {
            id: nextId(),
            role: "error",
            text: `Không gọi được backend: ${err instanceof Error ? err.message : String(err)}`,
          },
        ]);
      } finally {
        setLoading(false);
      }
    },
    [sessionId, loading]
  );

  const startNewConversation = useCallback(async () => {
    try {
      await resetSession(sessionId);
    } catch {
      // Backend có thể đang tắt — vẫn cho phép bắt đầu phiên mới ở client.
    }
    const id = newSessionId();
    localStorage.setItem(SESSION_STORAGE_KEY, id);
    setSessionId(id);
    setMessages([]);
  }, [sessionId]);

  return { sessionId, messages, loading, send, startNewConversation };
}
