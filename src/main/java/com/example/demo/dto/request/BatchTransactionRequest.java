
package com.example.demo.dto.request;

import jakarta.validation.Valid;

public record BatchTransactionRequest(

        @Valid DepositRequest deposit,

        @Valid WithdrawalRequest withdrawal,

        @Valid TransferRequest transfer

) {

    public BatchTransactionRequest {

        int count = 0;

        if (deposit != null) count++;
        if (withdrawal != null) count++;
        if (transfer != null) count++;

        if (count != 1) {
            throw new IllegalArgumentException(
                    "Exactly one transaction type is required"
            );
        }
    }

    public String transactionType() {

        if (deposit != null) {
            return "DEPOSIT";
        }

        if (withdrawal != null) {
            return "WITHDRAWAL";
        }

        return "TRANSFER";
    }
}
