package cl.duoc.backendiii.mscustomer.service;

import cl.duoc.backendiii.mscustomer.model.CustomerProfileRequest;
import cl.duoc.backendiii.mscustomer.model.CustomerProfileResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerServiceTest {

    private final FakeCustomerRepository repository = new FakeCustomerRepository();
    private final CustomerService service = new CustomerService(repository);

    @Test
    void createsCustomerProfileWithDefaults() {
        CustomerProfileResponse profile = service.create(new CustomerProfileRequest(
                101L,
                " Alex Fernandez ",
                "",
                null,
                null,
                null));

        assertThat(profile.customerId()).isEqualTo(101L);
        assertThat(profile.fullName()).isEqualTo("Alex Fernandez");
        assertThat(profile.segment()).isEqualTo("GENERAL");
        assertThat(profile.status()).isEqualTo("ACTIVE");
    }

    @Test
    void rejectsMissingName() {
        CustomerProfileRequest request = new CustomerProfileRequest(101L, "", null, null, null, null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nombre");
    }

    @Test
    void clampsListLimit() {
        service.customers(200);

        assertThat(repository.lastLimit).isEqualTo(100);
    }

    private static class FakeCustomerRepository extends CustomerRepository {
        private final List<CustomerProfileResponse> saved = new ArrayList<>();
        private int lastLimit;

        FakeCustomerRepository() {
            super(null);
        }

        @Override
        public CustomerProfileResponse save(CustomerProfileResponse profile) {
            saved.add(profile);
            return profile;
        }

        @Override
        public Optional<CustomerProfileResponse> findById(long customerId) {
            return saved.stream()
                    .filter(profile -> profile.customerId() == customerId)
                    .findFirst();
        }

        @Override
        public List<CustomerProfileResponse> findRecent(int limit) {
            lastLimit = limit;
            return saved;
        }
    }
}
