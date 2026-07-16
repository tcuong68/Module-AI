package com.roomfinder.chat.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.roomfinder.chat.domain.Intent;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Kết quả tầng NLU — hợp đồng JSON giữa NLU và Retrieval (§3.4).
 * Cùng schema dù cài đặt bằng LLM (GĐ1) hay PhoBERT (GĐ2).
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class NluResult {

    private Intent intent = Intent.OUT_OF_SCOPE;
    private double confidence = 0.9;      // LLM có thể không trả → mặc định 0.9
    private Filters entities = new Filters();
}
