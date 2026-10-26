package fr.equibook.booking;
import fr.equibook.core.Command;
import org.bson.Document;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
@RestController
@RequestMapping("/bookings")
public class Api {
    private final BookingDomain domain;
    public Api(BookingDomain domain) { this.domain = domain; }
    @GetMapping("/{id}")
    public Document get(@PathVariable String id) { return domain.get(id); }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Document create(@RequestBody Command command) { return domain.execute(command, "CreateBooking", null); }
    @PatchMapping("/{id}/confirm")
    public Document confirmBooking(@PathVariable String id, @RequestBody Command command) { return domain.execute(command, "ConfirmBooking", id); }
    @PatchMapping("/{id}/cancel")
    public Document cancelBooking(@PathVariable String id, @RequestBody Command command) { return domain.execute(command, "CancelBooking", id); }
    @PatchMapping("/{id}/complete")
    public Document completeBooking(@PathVariable String id, @RequestBody Command command) { return domain.execute(command, "CompleteBooking", id); }
    @GetMapping("/rider/{riderId}")
    public java.util.List<Document> forRider(@PathVariable String riderId) { return domain.forRider(riderId); }
}
