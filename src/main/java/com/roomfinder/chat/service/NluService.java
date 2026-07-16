package com.roomfinder.chat.service;

import com.roomfinder.chat.model.NluResult;

/**
 * Tầng NLU — QUYẾT ĐỊNH KIẾN TRÚC (§9.1): là INTERFACE.
 * GĐ1 cài đặt bằng LLM (LlmNluServiceImpl), GĐ2 thay bằng PhoBERT
 * mà không sửa dòng nào ở tầng trên → cho phép so sánh A/B (§14.4).
 */
public interface NluService {
    NluResult parse(String message);
}
