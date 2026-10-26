-- =============================================================================
-- IT Equipment Inventory System - Relational Database Schema (MySQL / MariaDB)
-- Production Ready Schema with Seed Data & Constraints
-- =============================================================================

CREATE DATABASE IF NOT EXISTS `it_inventory_db` 
CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `it_inventory_db`;

-- 1. USERS TABLE
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users` (
    `user_id` VARCHAR(64) NOT NULL PRIMARY KEY,
    `full_name` VARCHAR(128) NOT NULL,
    `email` VARCHAR(191) NOT NULL UNIQUE,
    `password` VARCHAR(255) NOT NULL, -- Hashed or plain for initial setup
    `role` ENUM('admin', 'scanner') NOT NULL DEFAULT 'scanner',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. EQUIPMENT ITEMS TABLE
DROP TABLE IF EXISTS `equipment_items`;
CREATE TABLE `equipment_items` (
    `item_id` VARCHAR(64) NOT NULL PRIMARY KEY,
    `asset_tag` VARCHAR(64) NOT NULL UNIQUE,
    `item_name` VARCHAR(255) NOT NULL,
    `category` ENUM('Laptop', 'Desktop', 'Monitor', 'Network', 'Peripheral') NOT NULL,
    `serial_number` VARCHAR(128) NOT NULL,
    `status` ENUM('Available', 'Assigned', 'Maintenance', 'Defective') NOT NULL DEFAULT 'Available',
    `assigned_to` VARCHAR(191) NULL DEFAULT '',
    `location` VARCHAR(191) NOT NULL DEFAULT 'IT Storage',
    `last_scanned_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `last_scanned_by` VARCHAR(128) NULL DEFAULT 'System Admin',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_asset_tag` (`asset_tag`),
    INDEX `idx_status` (`status`),
    INDEX `idx_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. SCAN LOGS (AUDIT TRAIL) TABLE
DROP TABLE IF EXISTS `scan_logs`;
CREATE TABLE `scan_logs` (
    `log_id` VARCHAR(64) NOT NULL PRIMARY KEY,
    `asset_tag` VARCHAR(64) NOT NULL,
    `item_name` VARCHAR(255) NOT NULL,
    `action_type` ENUM('Audit Verify', 'Check-Out / Assign', 'Check-In', 'Report Defective', 'New Registration') NOT NULL,
    `scanned_by` VARCHAR(128) NOT NULL,
    `location` VARCHAR(191) NOT NULL,
    `remarks` TEXT NULL,
    `timestamp` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_log_asset_tag` (`asset_tag`),
    INDEX `idx_log_timestamp` (`timestamp`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- SEED DATA
-- =============================================================================

-- Seed Users
INSERT INTO `users` (`user_id`, `full_name`, `email`, `password`, `role`) VALUES
('USR-ADMIN-01', 'Chief IT Administrator', 'admin@itinventory.com', 'admin123', 'admin'),
('USR-SCAN-01', 'Alex Mercer (Field Tech)', 'scanner@itinventory.com', 'scan123', 'scanner'),
('USR-SCAN-02', 'Samantha Vance (Helpdesk)', 'svance@itinventory.com', 'scan123', 'scanner');

-- Seed Equipment Items
INSERT INTO `equipment_items` 
(`item_id`, `asset_tag`, `item_name`, `category`, `serial_number`, `status`, `assigned_to`, `location`, `last_scanned_at`, `last_scanned_by`) 
VALUES
('EQ-001', 'IT-LAP-001', 'Dell Latitude 5420', 'Laptop', 'DL5420-9941X', 'Assigned', 'John Doe (Engineering)', 'Floor 3 - Desk 304', NOW() - INTERVAL 2 HOUR, 'Alex Mercer (Field Tech)'),
('EQ-002', 'IT-LAP-002', 'MacBook Pro 16" M2', 'Laptop', 'C02G99AAQ05D', 'Available', '', 'Room 302 - IT Vault', NOW() - INTERVAL 1 DAY, 'Chief IT Administrator'),
('EQ-003', 'IT-DSK-001', 'HP EliteDesk 800 G6', 'Desktop', 'HPE800-8812A', 'Assigned', 'Finance Department', 'Floor 2 - Finance Dept', NOW() - INTERVAL 5 HOUR, 'Alex Mercer (Field Tech)'),
('EQ-004', 'IT-MON-001', 'Dell UltraSharp U2723QE 4K', 'Monitor', 'CN-0K75P8-74261', 'Available', '', 'Room 302 - IT Vault', NOW() - INTERVAL 3 DAY, 'Samantha Vance (Helpdesk)'),
('EQ-005', 'IT-NET-001', 'Cisco Catalyst 9300 48-Port', 'Network', 'FCW2215L0K1', 'Working' /* Available */, '', 'Server Room Alpha - Rack B', NOW() - INTERVAL 4 HOUR, 'Chief IT Administrator'),
('EQ-006', 'IT-LAP-003', 'Lenovo ThinkPad X1 Carbon Gen 10', 'Laptop', 'PF-3B902K', 'Maintenance', 'Sarah Jenkins (Legal)', 'IT Repair Bench #2', NOW() - INTERVAL 30 MINUTE, 'Samantha Vance (Helpdesk)'),
('EQ-007', 'IT-PER-001', 'Logitech MX Master 3S', 'Peripheral', 'LZ2340M1', 'Assigned', 'Dev Team Lead', 'Floor 4 - Lab 1', NOW() - INTERVAL 6 HOUR, 'Alex Mercer (Field Tech)'),
('EQ-008', 'IT-MON-002', 'LG 34WN80C-B Ultrawide', 'Monitor', 'LG34-77189X', 'Defective', '', 'Defective IT Bin - Room 101', NOW() - INTERVAL 15 MINUTE, 'Alex Mercer (Field Tech)');

-- Seed Live Audit Logs
INSERT INTO `scan_logs` 
(`log_id`, `asset_tag`, `item_name`, `action_type`, `scanned_by`, `location`, `remarks`, `timestamp`) 
VALUES
('LOG-1001', 'IT-LAP-003', 'Lenovo ThinkPad X1 Carbon Gen 10', 'Report Defective', 'Samantha Vance (Helpdesk)', 'IT Repair Bench #2', 'Keyboard row Q-P unresponsive. Sent to internal bench.', NOW() - INTERVAL 30 MINUTE),
('EQ-LOG-1002', 'IT-MON-002', 'LG 34WN80C-B Ultrawide', 'Report Defective', 'Alex Mercer (Field Tech)', 'Defective IT Bin - Room 101', 'Screen flickering severely and vertical lines across LCD.', NOW() - INTERVAL 15 MINUTE),
('EQ-LOG-1003', 'IT-LAP-001', 'Dell Latitude 5420', 'Audit Verify', 'Alex Mercer (Field Tech)', 'Floor 3 - Desk 304', 'Routine quarterly inventory scan. Equipment verified in place.', NOW() - INTERVAL 2 HOUR),
('EQ-LOG-1004', 'IT-NET-001', 'Cisco Catalyst 9300 48-Port', 'Audit Verify', 'Chief IT Administrator', 'Server Room Alpha - Rack B', 'Firmware patch verified. Ports 1-48 operational.', NOW() - INTERVAL 4 HOUR),
('EQ-LOG-1005', 'IT-DSK-001', 'HP EliteDesk 800 G6', 'Check-Out / Assign', 'Alex Mercer (Field Tech)', 'Floor 2 - Finance Dept', 'Assigned to new accounting associate. Asset tagged & sealed.', NOW() - INTERVAL 5 HOUR);
