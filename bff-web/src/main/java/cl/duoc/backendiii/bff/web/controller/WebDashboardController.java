package cl.duoc.backendiii.bff.web.controller;

import cl.duoc.backendiii.bff.web.model.WebDashboardResponse;
import cl.duoc.backendiii.bff.web.service.WebDashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/web")
public class WebDashboardController {

    private final WebDashboardService service;

    public WebDashboardController(WebDashboardService service) {
        this.service = service;
    }

    @GetMapping("/cuentas/{accountId}/dashboard")
    public WebDashboardResponse dashboard(@PathVariable long accountId) {
        return service.dashboard(accountId);
    }
}

