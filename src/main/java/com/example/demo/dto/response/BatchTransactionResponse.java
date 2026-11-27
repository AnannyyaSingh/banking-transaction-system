
package com.example.demo.dto.response;

import java.util.List;

public record BatchTransactionResponse(

        int totalTransactions,

        long successfulTransactions,

        long failedTransactions,

        List<BatchTransactionResult> results

) {}
