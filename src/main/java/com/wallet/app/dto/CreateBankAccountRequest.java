package com.wallet.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateBankAccountRequest(
    @NotBlank
    @Size(max = 120)
    String accountHolderName,

    @NotBlank
    @Pattern(regexp = "^[0-9]{9,18}$", message = "accountNumber must be 9 to 18 digits")
    String accountNumber,

    @NotBlank
    @Pattern(regexp = "^[A-Z]{4}0[A-Z0-9]{6}$", message = "ifscCode must be a valid IFSC")
    String ifscCode,

    @NotBlank
    @Size(max = 120)
    String bankName
) {
}
