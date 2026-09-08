package com.rescueroom.backend.session;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailRequest(@NotBlank @Email @Size(max = 254) String email) {
    public EmailRequest { email = email == null ? null : email.strip(); }
}
