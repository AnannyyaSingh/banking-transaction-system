
package com.example.demo.dto.response;

public record BatchTransactionResult(

        int index,

        String transactionType,

        boolean success,

        TransactionResponse transaction,

        String errorMessage

) {}
