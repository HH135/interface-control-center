package com.hh135.icc;

import org.springframework.stereotype.Component;
import static com.hh135.icc.Models.*;

/** Deterministic local adapter; no external side effects or sleeping. */
@Component
public class SapSimulator {
    public record Result(boolean success, String errorCode, String detail) {}
    public Result send(Scenario scenario, int attempt) {
        return switch (scenario) {
            case SUCCESS -> new Result(true, null, "SAP simulator accepted contract");
            case FAIL_ONCE -> attempt == 1
                ? new Result(false, "SAP_UNAVAILABLE", "Simulated temporary SAP outage")
                : new Result(true, null, "SAP simulator recovered and accepted contract");
            case ALWAYS_FAIL -> new Result(false, "SAP_REJECTED", "Simulated SAP rejection");
            case TIMEOUT -> new Result(false, "SAP_TIMEOUT", "Simulated SAP timeout");
        };
    }
}
