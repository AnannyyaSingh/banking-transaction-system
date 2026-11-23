-- -- Sample data for Banking Transaction Processing System
-- -- Execute this script only once on a fresh database.

-- USE banking_transaction_system;

-- INSERT INTO customer (full_name, email, phone)
-- VALUES
-- ('Rahul Sharma', 'rahul@example.com', '9876543210'),
-- ('Priya Verma', 'priya@example.com', '9876543211'),
-- ('Amit Singh', 'amit@example.com', '9876543212'),
-- ('Sneha Gupta', 'sneha@example.com', '9876543213'),
-- ('Arjun Kumar', 'arjun@example.com', '9876543214');

-- INSERT INTO account
-- (customer_id, account_number, balance, account_status)
-- VALUES
-- (1, 'ACC10001', 10000.00, 'ACTIVE'),
-- (2, 'ACC10002', 15000.00, 'ACTIVE'),
-- (3, 'ACC10003', 20000.00, 'ACTIVE'),
-- (4, 'ACC10004', 5000.00, 'ACTIVE'),
-- (5, 'ACC10005', 12000.00, 'ACTIVE');

-- SELECT
--     c.customer_id,
--     c.full_name,
--     a.account_number,
--     a.balance,
--     a.account_status
-- FROM customer c
-- INNER JOIN account a
-- ON c.customer_id = a.customer_id;