import type { Filters } from "../types/chat";

const UTILITY_VI: Record<string, string> = {
  air_conditioner: "điều hòa",
  parking: "chỗ để xe",
  wifi: "wifi",
  washing_machine: "máy giặt",
};

function formatVnd(v: number): string {
  if (v % 1_000_000 === 0) return `${v / 1_000_000} triệu`;
  return `${v.toLocaleString("vi-VN")}đ`;
}

/**
 * Dựng lại câu tìm kiếm tự nhiên từ active_filters (bỏ qua field `omitKey` nếu có).
 * Dùng khi người dùng bấm "x" trên 1 chip filter — không có API xóa 1 filter riêng lẻ
 * (chatbot chỉ hỗ trợ RESET toàn bộ hoặc MERGE/OVERRIDE), nên ta gửi lại yêu cầu đầy đủ
 * dưới dạng tin nhắn chat bình thường (§10 bước 1.5 của SPEC).
 */
export function buildSearchSentence(
  filters: Filters,
  omitKey?: keyof Filters
): string {
  const parts: string[] = [];

  if (omitKey !== "price_max" && filters.price_max != null) {
    parts.push(`dưới ${formatVnd(filters.price_max)}`);
  }
  if (omitKey !== "price_min" && filters.price_min != null) {
    parts.push(`từ ${formatVnd(filters.price_min)} trở lên`);
  }
  if (omitKey !== "location" && filters.location) {
    parts.push(`ở ${filters.location}`);
  }
  if (omitKey !== "poi" && filters.poi) {
    parts.push(`gần ${filters.poi}`);
  }
  if (omitKey !== "area_min" && filters.area_min != null) {
    parts.push(`trên ${filters.area_min}m²`);
  }
  if (omitKey !== "utilities" && filters.utilities.length > 0) {
    const names = filters.utilities.map((u) => UTILITY_VI[u] ?? u);
    parts.push(`có ${names.join(", ")}`);
  }

  if (parts.length === 0) return "Tìm phòng khác";
  return `Tìm phòng ${parts.join(", ")}`;
}

/** Xóa riêng 1 tiện ích khỏi danh sách utilities, giữ nguyên các filter khác. */
export function buildSearchSentenceWithoutUtility(
  filters: Filters,
  utilityKey: string
): string {
  const remaining = { ...filters, utilities: filters.utilities.filter((u) => u !== utilityKey) };
  return buildSearchSentence(remaining);
}
