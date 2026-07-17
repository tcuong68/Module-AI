// Khớp đúng hợp đồng API §7.1 (SPEC) — xem ChatRequest/ChatResponse/Filters/RoomCardDto/MetaDto
// ở backend (com.roomfinder.chat.dto / .model). JSON dùng snake_case.

export interface ChatRequest {
  session_id?: string;
  message: string;
  user_id?: number;
}

export interface RoomCard {
  id: number;
  title: string;
  price: number;
  area: number;
  district: string;
  address: string;
  distance_m: number | null;
  thumbnail: string | null;
  url: string;
}

export interface Filters {
  price_min: number | null;
  price_max: number | null;
  location: string | null;
  poi: string | null;
  radius_m: number | null;
  area_min: number | null;
  utilities: string[];
  room_type: string | null;
  datetime: string | null;
  room_refs: number[];
}

export type ChatPath = "FAST" | "LLM" | "TEMPLATE" | "CLARIFY";

export interface ChatMeta {
  path: ChatPath;
  relaxed: boolean;
  latency_ms: number;
  nlu_confidence: number;
  hallucination_detected: boolean;
}

export interface ChatResponse {
  session_id: string;
  reply: string;
  intent: string;
  rooms: RoomCard[];
  active_filters: Filters;
  meta: ChatMeta;
}

export interface ResetResponse {
  ok: boolean;
  session_id: string;
}
