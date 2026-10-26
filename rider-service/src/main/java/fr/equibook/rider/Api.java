package fr.equibook.rider;
import fr.equibook.core.Command;
import org.bson.Document;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
@RestController
@RequestMapping("/riders")
public class Api {
    private final RiderDomain domain;
    public Api(RiderDomain domain) { this.domain = domain; }
    @GetMapping("/{id}")
    public Document get(@PathVariable String id) { return domain.get(id); }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Document create(@RequestBody Command command) { return domain.execute(command, "CreateRider", null); }
    @PutMapping("/{id}")
    public Document updateRider(@PathVariable String id, @RequestBody Command command) { return domain.execute(command, "UpdateRider", id); }
    @PatchMapping("/{id}/level")
    public Document changeRiderLevel(@PathVariable String id, @RequestBody Command command) { return domain.execute(command, "ChangeRiderLevel", id); }
}
