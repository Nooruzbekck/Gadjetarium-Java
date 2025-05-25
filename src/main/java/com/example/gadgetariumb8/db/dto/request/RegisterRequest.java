package com.example.gadgetariumb8.db.dto.request;

import com.example.gadgetariumb8.db.validation.NameValid;
import com.example.gadgetariumb8.db.validation.PasswordValid;
import com.example.gadgetariumb8.db.validation.PhoneNumberValid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "first name should not be empty")
        @NameValid(message = "first name should be only letters and length must be between 2 and 33 characters!")
        String firstName,
        @NotBlank(message = "last name should not be empty")
        @NameValid(message = "last name should be only letters and length must be between 2 and 33 characters!")
        String lastName,
        @NotBlank(message = "phone number should not be empty")
        @PhoneNumberValid(message = "Phone number should start with +996, consist of 13 characters and must be valid!")
        String phoneNumber,
        @NotBlank(message = "email should not be empty")
        @Email(message = "Write valid email!")
        String email,
        @NotBlank(message = "password should not be empty")
        @PasswordValid(message = "Password length must be more than 8 symbols," +
                " and contain least one capital letter!")
        String password
) {
}
