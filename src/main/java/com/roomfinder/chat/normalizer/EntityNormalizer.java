package com.roomfinder.chat.normalizer;

import com.roomfinder.chat.model.Filters;
import org.springframework.stereotype.Component;

/**
 * Tầng Normalizer (§3.3) — BẮT BUỘC. Chuyển entity "thô" từ NLU thành
 * giá trị máy đọc được trước khi vào Retrieval. Ở GĐ2 (PhoBERT NER trả span)
 * tầng này gánh phần lớn công việc; ở GĐ1 nó là mạng an toàn cho LLM.
 */
@Component
public class EntityNormalizer {

    public void normalize(Filters f) {
        if (f == null) return;

        // Tiện ích: đồng nghĩa + bỏ dấu → enum key
        f.setUtilities(UtilityNormalizer.normalizeList(f.getUtilities()));

        // Khu vực: fuzzy match tên quận chuẩn
        if (f.getLocation() != null) {
            f.setLocation(LocationNormalizer.normalize(f.getLocation()));
        }

        // POI: chỉ trim (khớp alias xử lý ở RetrievalService)
        if (f.getPoi() != null) {
            String p = f.getPoi().trim();
            f.setPoi(p.isEmpty() ? null : p);
        }

        // DateTime: chuẩn hóa sang ISO nếu đang là span tiếng Việt
        if (f.getDatetime() != null && !isIso(f.getDatetime())) {
            f.setDatetime(DateTimeNormalizer.normalize(f.getDatetime()));
        }
    }

    private boolean isIso(String s) {
        return s.matches("\\d{4}-\\d{2}-\\d{2}T.*");
    }
}
