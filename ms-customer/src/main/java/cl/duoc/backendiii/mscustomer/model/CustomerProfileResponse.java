package cl.duoc.backendiii.mscustomer.model;

import java.time.LocalDateTime;

public record CustomerProfileResponse(
        long customerId,
        String fullName,
        String email,
        String phone,
        String segment,
        String status,
        LocalDateTime updatedAt
) {
}
