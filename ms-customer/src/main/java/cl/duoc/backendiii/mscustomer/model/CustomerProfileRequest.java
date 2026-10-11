package cl.duoc.backendiii.mscustomer.model;

public record CustomerProfileRequest(
        Long customerId,
        String fullName,
        String email,
        String phone,
        String segment,
        String status
) {
}
