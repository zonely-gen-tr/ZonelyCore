package dev.zonely.whiteeffect.database.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;


public final class ChatBridgeSchema {

    private static final Map<String, String> TABLES;

    static {
        Map<String, String> tables = new LinkedHashMap<>();

        tables.put("ZonelyCoreChatLog",
                "CREATE TABLE IF NOT EXISTS `ZonelyCoreChatLog` (\n" +
                        "  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,\n" +
                        "  `server` VARCHAR(64) NOT NULL,\n" +
                        "  `channel` VARCHAR(32) NOT NULL DEFAULT 'global',\n" +
                        "  `source` VARCHAR(64) NOT NULL,\n" +
                        "  `uuid` VARCHAR(36) NULL,\n" +
                        "  `role_key` VARCHAR(32) NULL,\n" +
                        "  `role_prefix` VARCHAR(64) NULL,\n" +
                        "  `display_name` VARCHAR(96) NOT NULL,\n" +
                        "  `message_plain` TEXT NOT NULL,\n" +
                        "  `message_colored` TEXT NOT NULL,\n" +
                        "  `meta_json` TEXT NULL,\n" +
                        "  `created_at` BIGINT NOT NULL,\n" +
                        "  PRIMARY KEY (`id`),\n" +
                        "  INDEX `chat_server_created_idx` (`server`, `created_at`),\n" +
                        "  INDEX `chat_source_idx` (`source`)\n" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;");

        tables.put("ZonelyCoreChatQueue",
                "CREATE TABLE IF NOT EXISTS `ZonelyCoreChatQueue` (\n" +
                        "  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,\n" +
                        "  `server` VARCHAR(64) NOT NULL,\n" +
                        "  `type` VARCHAR(16) NOT NULL,\n" +
                        "  `payload` TEXT NOT NULL,\n" +
                        "  `sender` VARCHAR(64) NULL,\n" +
                        "  `requires_op` TINYINT(1) NOT NULL DEFAULT 0,\n" +
                        "  `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING',\n" +
                        "  `result_message` TEXT NULL,\n" +
                        "  `created_at` BIGINT NOT NULL,\n" +
                        "  `processed_at` BIGINT NOT NULL DEFAULT 0,\n" +
                        "  PRIMARY KEY (`id`),\n" +
                        "  INDEX `queue_server_status_idx` (`server`, `status`, `id`)\n" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;");

        tables.put("ZonelyCoreOnlinePlayers",
                "CREATE TABLE IF NOT EXISTS `ZonelyCoreOnlinePlayers` (\n" +
                        "  `uuid` VARCHAR(36) NOT NULL,\n" +
                        "  `name` VARCHAR(64) NOT NULL,\n" +
                        "  `display_name` VARCHAR(96) NOT NULL,\n" +
                        "  `server` VARCHAR(64) NOT NULL,\n" +
                        "  `role_key` VARCHAR(32) NULL,\n" +
                        "  `role_prefix` VARCHAR(64) NULL,\n" +
                        "  `is_op` TINYINT(1) NOT NULL DEFAULT 0,\n" +
                        "  `online` TINYINT(1) NOT NULL DEFAULT 1,\n" +
                        "  `last_seen` BIGINT NOT NULL,\n" +
                        "  PRIMARY KEY (`uuid`),\n" +
                        "  INDEX `online_server_idx` (`server`),\n" +
                        "  INDEX `online_name_idx` (`name`)\n" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;");

        tables.put("ZonelyCoreInventorySnapshots",
                "CREATE TABLE IF NOT EXISTS `ZonelyCoreInventorySnapshots` (\n" +
                        "  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,\n" +
                        "  `server` VARCHAR(64) NOT NULL,\n" +
                        "  `uuid` VARCHAR(36) NOT NULL,\n" +
                        "  `name` VARCHAR(64) NOT NULL,\n" +
                        "  `profile_id` BIGINT UNSIGNED NULL,\n" +
                        "  `data_json` LONGTEXT NOT NULL,\n" +
                        "  `captured_at` BIGINT NOT NULL,\n" +
                        "  PRIMARY KEY (`id`),\n" +
                        "  UNIQUE KEY `inventory_unique_idx` (`server`, `uuid`),\n" +
                        "  INDEX `inventory_profile_idx` (`profile_id`),\n" +
                        "  INDEX `inventory_captured_idx` (`captured_at`)\n" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;");

        TABLES = Collections.unmodifiableMap(tables);
    }

    private ChatBridgeSchema() {
    }

    public static Map<String, String> tables() {
        return TABLES;
    }
}
