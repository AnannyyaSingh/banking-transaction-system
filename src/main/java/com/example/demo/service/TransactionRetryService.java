
package com.example.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.function.Supplier;

@Service
public class TransactionRetryService {

    private static final Logger logger =
            LoggerFactory.getLogger(
                    TransactionRetryService.class
            );

    private static final int MAX_ATTEMPTS = 3;

    private static final long INITIAL_DELAY_MS = 100;

    public <T> T executeWithRetry(
            String operation,
            Supplier<T> action) {

        long delay = INITIAL_DELAY_MS;

        for (int attempt = 1; ; attempt++) {

            try {

                return action.get();

            } catch (RuntimeException exception) {

                if (!isRetryable(exception)
                        || attempt >= MAX_ATTEMPTS) {

                    throw exception;
                }

                logger.warn(
                        "Retryable transaction failure: "
                        + "operation={}, attempt={}, "
                        + "nextDelayMs={}, thread={}",
                        operation,
                        attempt,
                        delay,
                        Thread.currentThread().getName()
                );

                try {

                    Thread.sleep(delay);

                } catch (InterruptedException interrupted) {

                    Thread.currentThread().interrupt();

                    throw new IllegalStateException(
                            "Transaction retry interrupted",
                            interrupted
                    );
                }

                delay *= 2;
            }
        }
    }

    private boolean isRetryable(Throwable exception) {

        Throwable cause = exception;

        while (cause != null) {

            if (cause instanceof
                    PessimisticLockingFailureException) {

                return true;
            }

            if (cause instanceof SQLException sqlException) {

                int errorCode = sqlException.getErrorCode();

                // MySQL deadlock or lock wait timeout
                if (errorCode == 1213 || errorCode == 1205) {
                    return true;
                }
            }

            cause = cause.getCause();
        }

        return false;
    }
}
