import type { ChatRequest, ChatResponse, ResetResponse } from "../types/chat";

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

async function parseJsonOrThrow(res: Response) {
  if (!res.ok) {
    const text = await res.text().catch(() => "");
    throw new Error(`HTTP ${res.status}: ${text || res.statusText}`);
  }
  return res.json();
}

export async function sendMessage(payload: ChatRequest): Promise<ChatResponse> {
  const res = await fetch(`${BASE_URL}/api/v1/chat`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  return parseJsonOrThrow(res);
}

export async function resetSession(sessionId: string): Promise<ResetResponse> {
  const res = await fetch(`${BASE_URL}/api/v1/chat/reset`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ session_id: sessionId }),
  });
  return parseJsonOrThrow(res);
}
