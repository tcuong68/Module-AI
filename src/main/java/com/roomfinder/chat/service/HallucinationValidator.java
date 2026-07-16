package com.roomfinder.chat.service;

import com.roomfinder.chat.domain.Room;
import com.roomfinder.chat.model.ValidationResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Guardrail chống Hallucination — §6.4. Thành phần quan trọng nhất về mặt
 * học thuật: vừa là cơ chế an toàn (chặn cứng), vừa là chỉ số đo được (DoD-4).
 *
 * Nguyên tắc: mọi mã phòng [#id] và mọi con số tiền LLM nhắc tới PHẢI khớp
 * dữ liệu trong context (last_result là nguồn chân lý).
 */
@Component
public class HallucinationValidator {

    private static final Pattern ROOM_ID = Pattern.compile("\\[#(\\d+)\\]");
    private static final Pattern MONEY =
            Pattern.compile("([\\d.,]{4,})\\s*(đ|vnđ|vnd|đồng)", Pattern.CASE_INSENSITIVE);

    public ValidationResult validate(String llmOutput, List<Room> context) {
        if (llmOutput == null) return ValidationResult.fail("EMPTY_OUTPUT");

        Set<Long> allowedIds = context.stream()
                .map(Room::getId).collect(Collectors.toSet());
        Set<Long> allowedPrices = context.stream()
                .map(Room::getPrice).collect(Collectors.toSet());

        // 1. Mọi mã phòng nhắc tới phải nằm trong context
        Matcher m = ROOM_ID.matcher(llmOutput);
        while (m.find()) {
            long id = Long.parseLong(m.group(1));
            if (!allowedIds.contains(id)) {
                return ValidationResult.fail("PHANTOM_ROOM_ID:" + id);
            }
        }

        // 2. Mọi con số tiền phải khớp một phòng trong context
        m = MONEY.matcher(llmOutput);
        while (m.find()) {
            String digits = m.group(1).replaceAll("[.,]", "");
            if (digits.isEmpty()) continue;
            long price = Long.parseLong(digits);
            if (!allowedPrices.contains(price)) {
                return ValidationResult.fail("PHANTOM_PRICE:" + price);
            }
        }
        return ValidationResult.pass();
    }
}
