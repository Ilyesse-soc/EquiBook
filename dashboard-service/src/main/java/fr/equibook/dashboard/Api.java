package fr.equibook.dashboard;
import fr.equibook.core.Command;
import org.bson.Document;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
@RestController
@RequestMapping("/dashboard/riders")
public class Api {
    private final DashboardDomain domain;
    public Api(DashboardDomain domain) { this.domain = domain; }
    @GetMapping("/{id}")
    public Document get(@PathVariable String id) { return domain.get(id); }
}
