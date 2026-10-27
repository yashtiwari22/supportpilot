package com.supportpilot.ticket;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for creating/ingesting a customer support ticket.
 */
public record CreateTicketRequest(
    @NotBlank(message = "Customer email is required")
    @Email(message = "Customer email must be valid")
    String customerEmail,

    @NotBlank(message = "Customer name is required")
    @Size(max = 150, message = "Customer name must not exceed 150 characters")
    String customerName,

    @NotBlank(message = "Subject is required")
    @Size(max = 255, message = "Subject must not exceed 255 characters")
    String subject,

    @NotBlank(message = "Body is required")
    String body
) {}
