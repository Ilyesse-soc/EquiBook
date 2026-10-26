package fr.equibook.payment;
import fr.equibook.core.Command;
import org.bson.Document;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
@RestController
@RequestMapping("/payments")
public class Api {
    private final PaymentDomain domain;
    public Api(PaymentDomain domain) { this.domain = domain; }
    @GetMapping("/{id}")
    public Document get(@PathVariable String id) { return domain.get(id); }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Document create(@RequestBody Command command) { return domain.execute(command, "CreatePayment", null); }
    @PatchMapping("/{id}/confirm")
    public Document confirmPayment(@PathVariable String id, @RequestBody Command command) { return domain.execute(command, "ConfirmPayment", id); }
    @PatchMapping("/{id}/fail")
    public Document failPayment(@PathVariable String id, @RequestBody Command command) { return domain.execute(command, "FailPayment", id); }
    @PatchMapping("/{id}/refund")
    public Document refundPayment(@PathVariable String id, @RequestBody Command command) { return domain.execute(command, "RefundPayment", id); }
}
