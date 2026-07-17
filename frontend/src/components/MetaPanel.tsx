import { useState } from "react";
import type { ChatMeta } from "../types/chat";

const PATH_LABEL: Record<string, string> = {
  FAST: "Fast-path (mẫu câu, bỏ qua LLM)",
  LLM: "LLM-path (Gemini sinh câu trả lời)",
  TEMPLATE: "Template an toàn (guardrail chặn/LLM lỗi)",
  CLARIFY: "Hỏi lại (thiếu slot)",
};

/** Panel kỹ thuật gấp/mở — thay việc phải mở DevTools khi demo bảo vệ (§7.1 SPEC). */
export function MetaPanel({ meta }: { meta: ChatMeta }) {
  const [open, setOpen] = useState(false);

  return (
    <div className="meta-panel">
      <button type="button" className="meta-panel__toggle" onClick={() => setOpen((v) => !v)}>
        {open ? "▾" : "▸"} Chi tiết kỹ thuật ({meta.path})
      </button>
      {open && (
        <dl className="meta-panel__body">
          <dt>Đường xử lý</dt>
          <dd>{PATH_LABEL[meta.path] ?? meta.path}</dd>
          <dt>Độ tin cậy NLU</dt>
          <dd>{(meta.nlu_confidence * 100).toFixed(1)}%</dd>
          <dt>Đã nới lỏng điều kiện?</dt>
          <dd>{meta.relaxed ? "Có" : "Không"}</dd>
          <dt>Phát hiện bịa (hallucination)?</dt>
          <dd className={meta.hallucination_detected ? "meta-panel__warn" : undefined}>
            {meta.hallucination_detected ? "Có — đã chặn" : "Không"}
          </dd>
          <dt>Độ trễ</dt>
          <dd>{meta.latency_ms}ms</dd>
        </dl>
      )}
    </div>
  );
}
