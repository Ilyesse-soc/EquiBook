package fr.equibook.horse;
import fr.equibook.core.Command;
import org.bson.Document;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
@RestController
@RequestMapping("/horses")
public class Api {
    private final HorseDomain domain;
    public Api(HorseDomain domain) { this.domain = domain; }
    @GetMapping("/{id}")
    public Document get(@PathVariable String id) { return domain.get(id); }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Document create(@RequestBody Command command) { return domain.execute(command, "CreateHorse", null); }
    @PutMapping("/{id}")
    public Document updateHorse(@PathVariable String id, @RequestBody Command command) { return domain.execute(command, "UpdateHorse", id); }
    @PatchMapping("/{id}/availability")
    public Document changeHorseAvailability(@PathVariable String id, @RequestBody Command command) { return domain.execute(command, "ChangeHorseAvailability", id); }
}
