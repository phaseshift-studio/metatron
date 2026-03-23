/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 *  Copyright (C) 2025- PhaseShift Studio, LLC
 *  
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

-- Migration script for MQTT-indexed objs table
-- This creates a table with virtual generated columns for efficient MQTT-style pattern matching
-- Compatible with MariaDB 10.2+ and MySQL 5.7+

CREATE TABLE IF NOT EXISTS objs (
    furi VARCHAR(512) NOT NULL PRIMARY KEY,
    obj TEXT NOT NULL,

    -- Virtual generated columns for path segments
    -- These are automatically computed from the furi column
    seg1 VARCHAR(128) AS (
        CASE
            WHEN SUBSTRING_INDEX(SUBSTRING_INDEX(furi, '/', 2), '/', -1) = '' THEN NULL
            ELSE SUBSTRING_INDEX(SUBSTRING_INDEX(furi, '/', 2), '/', -1)
        END
    ) VIRTUAL,

    seg2 VARCHAR(128) AS (
        CASE
            WHEN SUBSTRING_INDEX(SUBSTRING_INDEX(furi, '/', 3), '/', -1) = '' THEN NULL
            WHEN SUBSTRING_INDEX(SUBSTRING_INDEX(furi, '/', 3), '/', -1) = SUBSTRING_INDEX(SUBSTRING_INDEX(furi, '/', 2), '/', -1) THEN NULL
            ELSE SUBSTRING_INDEX(SUBSTRING_INDEX(furi, '/', 3), '/', -1)
        END
    ) VIRTUAL,

    seg3 VARCHAR(128) AS (
        CASE
            WHEN SUBSTRING_INDEX(SUBSTRING_INDEX(furi, '/', 4), '/', -1) = '' THEN NULL
            WHEN SUBSTRING_INDEX(SUBSTRING_INDEX(furi, '/', 4), '/', -1) = SUBSTRING_INDEX(SUBSTRING_INDEX(furi, '/', 3), '/', -1) THEN NULL
            ELSE SUBSTRING_INDEX(SUBSTRING_INDEX(furi, '/', 4), '/', -1)
        END
    ) VIRTUAL,

    seg4 VARCHAR(128) AS (
        CASE
            WHEN SUBSTRING_INDEX(SUBSTRING_INDEX(furi, '/', 5), '/', -1) = '' THEN NULL
            WHEN SUBSTRING_INDEX(SUBSTRING_INDEX(furi, '/', 5), '/', -1) = SUBSTRING_INDEX(SUBSTRING_INDEX(furi, '/', 4), '/', -1) THEN NULL
            ELSE SUBSTRING_INDEX(SUBSTRING_INDEX(furi, '/', 5), '/', -1)
        END
    ) VIRTUAL,

    seg5 VARCHAR(128) AS (
        CASE
            WHEN SUBSTRING_INDEX(SUBSTRING_INDEX(furi, '/', 6), '/', -1) = '' THEN NULL
            WHEN SUBSTRING_INDEX(SUBSTRING_INDEX(furi, '/', 6), '/', -1) = SUBSTRING_INDEX(SUBSTRING_INDEX(furi, '/', 5), '/', -1) THEN NULL
            ELSE SUBSTRING_INDEX(SUBSTRING_INDEX(furi, '/', 6), '/', -1)
        END
    ) VIRTUAL,

    -- Indexes on virtual columns for fast pattern matching
    INDEX idx_seg1 (seg1),
    INDEX idx_seg2 (seg2),
    INDEX idx_seg3 (seg3),
    INDEX idx_seg4 (seg4),
    INDEX idx_seg5 (seg5),

    -- Composite indexes for common multi-segment patterns
    INDEX idx_seg1_seg2 (seg1, seg2),
    INDEX idx_seg1_seg2_seg3 (seg1, seg2, seg3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Example usage:
--
-- Insert data:
-- INSERT INTO objs (furi, obj) VALUES ('/sensor/kitchen/temperature', '{"value": 22.5}');
-- INSERT INTO objs (furi, obj) VALUES ('/sensor/bedroom/temperature', '{"value": 20.1}');
-- INSERT INTO objs (furi, obj) VALUES ('/sensor/kitchen/humidity', '{"value": 45}');
--
-- MQTT pattern queries:
--
-- Match /sensor/+/temperature (any sensor's temperature):
-- SELECT * FROM objs WHERE seg1 = 'sensor' AND seg3 = 'temperature' AND seg4 IS NULL;
--
-- Match /sensor/# (all sensor topics):
-- SELECT * FROM objs WHERE seg1 = 'sensor';
--
-- Match /sensor/kitchen/+ (all kitchen sensor readings):
-- SELECT * FROM objs WHERE seg1 = 'sensor' AND seg2 = 'kitchen' AND seg4 IS NULL;
