package cl.duoc.backendiii.bff.mobile.controller;

import cl.duoc.backendiii.bff.mobile.model.MobileHomeResponse;
import cl.duoc.backendiii.bff.mobile.service.MobileHomeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mobile")
public class MobileHomeController {

    private final MobileHomeService service;

    public MobileHomeController(MobileHomeService service) {
        this.service = service;
    }

    @GetMapping("/cuentas/{accountId}/inicio")
    public MobileHomeResponse home(@PathVariable long accountId) {
        return service.home(accountId);
    }
}

