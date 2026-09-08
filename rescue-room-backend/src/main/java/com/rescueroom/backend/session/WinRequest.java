package com.rescueroom.backend.session;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record WinRequest(@NotNull @Min(0) @Max(90) Integer remainingSeconds) { }
