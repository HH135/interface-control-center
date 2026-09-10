package com.hh135.icc;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.time.LocalDate;
import java.util.*;
import static com.hh135.icc.Models.*;

@RestController
@RequestMapping("/api")
@Validated
public class IccController {
    private final IccService service;
    private final IccMapper mapper;
    public IccController(IccService service, IccMapper mapper) { this.service = service; this.mapper = mapper; }
    @PostMapping("/contracts") @ResponseStatus(HttpStatus.CREATED)
    public Contract create(@Valid @RequestBody CreateContract request) { return service.create(request); }
    @GetMapping("/contracts/{id}") public Contract contract(@PathVariable UUID id) { return service.contract(id); }
    @GetMapping("/contracts") public List<Contract> contracts(@RequestParam(defaultValue="50") @Min(1) @Max(100) int limit, @RequestParam(defaultValue="0") @Min(0) int offset) { return mapper.contracts(limit, offset); }
    @PostMapping("/contracts/{id}/send") @ResponseStatus(HttpStatus.CREATED)
    public Message send(@PathVariable UUID id, @Valid @RequestBody SendRequest request) { return service.send(id, request.scenario()); }
    @GetMapping("/messages/{id}") public Message message(@PathVariable UUID id) { return service.message(id); }
    @GetMapping("/messages") public List<Message> messages(@RequestParam(defaultValue="50") @Min(1) @Max(100) int limit, @RequestParam(defaultValue="0") @Min(0) int offset) { return mapper.messages(limit, offset); }
    @PostMapping("/messages/{id}/retry") public Message retry(@PathVariable UUID id) { return service.retry(id); }
    @GetMapping("/messages/{id}/history")
    public List<History> history(@PathVariable UUID id, @RequestParam(defaultValue="50") @Min(1) @Max(100) int limit, @RequestParam(defaultValue="0") @Min(0) int offset) {
        service.message(id); return mapper.history(id, limit, offset);
    }
    @GetMapping("/reports/daily") public DailySummary summary(@RequestParam LocalDate date) { return service.summary(date); }
    @GetMapping("/batches") public List<Batch> batches(@RequestParam(defaultValue="50") @Min(1) @Max(100) int limit, @RequestParam(defaultValue="0") @Min(0) int offset) { return mapper.batches(limit, offset); }
}
