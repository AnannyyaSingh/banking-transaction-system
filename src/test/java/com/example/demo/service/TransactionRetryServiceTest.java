
package com.example.demo.service;

import org.junit.jupiter.api.Test;
import org.springframework.dao.PessimisticLockingFailureException;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TransactionRetryServiceTest {

    private final TransactionRetryService retryService =
            new TransactionRetryService();

    @Test
    void shouldSucceedOnFirstAttempt() {

        AtomicInteger attempts = new AtomicInteger();

        String result = retryService.executeWithRetry(
                "DEPOSIT",
                () -> {
                    attempts.incrementAndGet();
                    return "SUCCESS";
                }
        );

        assertEquals("SUCCESS", result);
        assertEquals(1, attempts.get());
    }

    @Test
    void shouldRetryAfterLockingFailure() {

        AtomicInteger attempts = new AtomicInteger();

        String result = retryService.executeWithRetry(
                "TRANSFER",
                () -> {

                    if (attempts.incrementAndGet() == 1) {

                        throw new PessimisticLockingFailureException(
                                "Temporary locking failure"
                        );
                    }

                    return "SUCCESS";
                }
        );

        assertEquals("SUCCESS", result);
        assertEquals(2, attempts.get());
    }

    @Test
    void shouldSucceedOnThirdAttempt() {

        AtomicInteger attempts = new AtomicInteger();

        String result = retryService.executeWithRetry(
                "TRANSFER",
                () -> {

                    if (attempts.incrementAndGet() < 3) {

                        throw new PessimisticLockingFailureException(
                                "Temporary locking failure"
                        );
                    }

                    return "SUCCESS";
                }
        );

        assertEquals("SUCCESS", result);
        assertEquals(3, attempts.get());
    }

    @Test
    void shouldStopAfterMaximumAttempts() {

        AtomicInteger attempts = new AtomicInteger();

        assertThrows(
                PessimisticLockingFailureException.class,
                () -> retryService.executeWithRetry(
                        "TRANSFER",
                        () -> {

                            attempts.incrementAndGet();

                            throw new PessimisticLockingFailureException(
                                    "Persistent locking failure"
                            );
                        }
                )
        );

        assertEquals(3, attempts.get());
    }

    @Test
    void shouldNotRetryNonRetryableException() {

        AtomicInteger attempts = new AtomicInteger();

        assertThrows(
                IllegalArgumentException.class,
                () -> retryService.executeWithRetry(
                        "WITHDRAWAL",
                        () -> {

                            attempts.incrementAndGet();

                            throw new IllegalArgumentException(
                                    "Invalid transaction"
                            );
                        }
                )
        );

        assertEquals(1, attempts.get());
    }

    @Test
    void shouldRetryMySqlDeadlock() {

        AtomicInteger attempts = new AtomicInteger();

        String result = retryService.executeWithRetry(
                "TRANSFER",
                () -> {

                    if (attempts.incrementAndGet() == 1) {

                        throw new RuntimeException(
                                new SQLException(
                                        "Deadlock detected",
                                        "40001",
                                        1213
                                )
                        );
                    }

                    return "SUCCESS";
                }
        );

        assertEquals("SUCCESS", result);
        assertEquals(2, attempts.get());
    }

    @Test
    void shouldRetryMySqlLockWaitTimeout() {

        AtomicInteger attempts = new AtomicInteger();

        String result = retryService.executeWithRetry(
                "DEPOSIT",
                () -> {

                    if (attempts.incrementAndGet() == 1) {

                        throw new RuntimeException(
                                new SQLException(
                                        "Lock wait timeout",
                                        "HY000",
                                        1205
                                )
                        );
                    }

                    return "SUCCESS";
                }
        );

        assertEquals("SUCCESS", result);
        assertEquals(2, attempts.get());
    }
}
