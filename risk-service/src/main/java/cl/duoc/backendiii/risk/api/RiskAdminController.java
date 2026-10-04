package cl.duoc.backendiii.risk.api;

import cl.duoc.backendiii.risk.service.RiskEvaluationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/risk")
public class RiskAdminController {
    private final RiskEvaluationService service;

    public RiskAdminController(RiskEvaluationService service) {
        this.service = service;
    }

    @PostMapping("/admin/failure")
    public Map<String, Boolean> failureMode(@RequestParam boolean enabled) {
        service.setForcedFailure(enabled);
        return Map.of("forcedFailure", service.isForcedFailure());
    }
}
