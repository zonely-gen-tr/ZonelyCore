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

        tables.put("ZonelyCoreFriendRequests",
                "CREATE TABLE IF NOT EXISTS `ZonelyCoreFriendRequests` (\n" +
                        "  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,\n" +
                        "  `requester_uuid` VARCHAR(36) NOT NULL,\n" +
                        "  `requester_name` VARCHAR(32) NOT NULL,\n" +
                        "  `target_uuid` VARCHAR(36) NOT NULL,\n" +
                        "  `target_name` VARCHAR(32) NOT NULL,\n" +
                        "  `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING',\n" +
                        "  `created_at` BIGINT NOT NULL,\n" +
                        "  `responded_at` BIGINT NOT NULL DEFAULT 0,\n" +
                        "  PRIMARY KEY (`id`),\n" +
                        "  INDEX `friend_req_target_idx` (`target_uuid`, `status`, `id`),\n" +
                        "  INDEX `friend_req_requester_idx` (`requester_uuid`, `status`, `id`),\n" +
                        "  UNIQUE KEY `friend_req_unique_pending` (`requester_uuid`, `target_uuid`, `status`)\n" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;");

        tables.put("ZonelyCoreFriendRelations",
                "CREATE TABLE IF NOT EXISTS `ZonelyCoreFriendRelations` (\n" +
                        "  `user_low_uuid` VARCHAR(36) NOT NULL,\n" +
                        "  `user_low_name` VARCHAR(32) NOT NULL,\n" +
                        "  `user_high_uuid` VARCHAR(36) NOT NULL,\n" +
                        "  `user_high_name` VARCHAR(32) NOT NULL,\n" +
                        "  `created_at` BIGINT NOT NULL,\n" +
                        "  PRIMARY KEY (`user_low_uuid`, `user_high_uuid`),\n" +
                        "  INDEX `friend_rel_low_idx` (`user_low_uuid`),\n" +
                        "  INDEX `friend_rel_high_idx` (`user_high_uuid`)\n" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;");

        tables.put("ZonelyCoreFriendMessages",
                "CREATE TABLE IF NOT EXISTS `ZonelyCoreFriendMessages` (\n" +
                        "  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,\n" +
                        "  `sender_uuid` VARCHAR(36) NOT NULL,\n" +
                        "  `sender_name` VARCHAR(32) NOT NULL,\n" +
                        "  `target_uuid` VARCHAR(36) NOT NULL,\n" +
                        "  `target_name` VARCHAR(32) NOT NULL,\n" +
                        "  `kind` VARCHAR(16) NOT NULL DEFAULT 'DIRECT',\n" +
                        "  `message` VARCHAR(512) NOT NULL,\n" +
                        "  `server_from` VARCHAR(64) NULL,\n" +
                        "  `created_at` BIGINT NOT NULL,\n" +
                        "  PRIMARY KEY (`id`),\n" +
                        "  INDEX `friend_msg_target_idx` (`target_uuid`, `created_at`),\n" +
                        "  INDEX `friend_msg_sender_idx` (`sender_uuid`, `created_at`)\n" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;");

        tables.put("ZonelyCoreFriendMailbox",
                "CREATE TABLE IF NOT EXISTS `ZonelyCoreFriendMailbox` (\n" +
                        "  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,\n" +
                        "  `target_uuid` VARCHAR(36) NOT NULL,\n" +
                        "  `target_name` VARCHAR(32) NULL,\n" +
                        "  `kind` VARCHAR(24) NOT NULL,\n" +
                        "  `payload` TEXT NOT NULL,\n" +
                        "  `created_at` BIGINT NOT NULL,\n" +
                        "  `delivered_at` BIGINT NOT NULL DEFAULT 0,\n" +
                        "  PRIMARY KEY (`id`),\n" +
                        "  INDEX `friend_mailbox_target_idx` (`target_uuid`, `delivered_at`, `id`)\n" +
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

        tables.put("ZonelyCorePlayerActivityDaily",
                "CREATE TABLE IF NOT EXISTS `ZonelyCorePlayerActivityDaily` (\n" +
                        "  `uuid` VARCHAR(36) NOT NULL,\n" +
                        "  `name` VARCHAR(64) NOT NULL,\n" +
                        "  `date_key` CHAR(8) NOT NULL,\n" +
                        "  `online_millis` BIGINT UNSIGNED NOT NULL DEFAULT 0,\n" +
                        "  `message_count` INT UNSIGNED NOT NULL DEFAULT 0,\n" +
                        "  `command_count` INT UNSIGNED NOT NULL DEFAULT 0,\n" +
                        "  `last_active_at` BIGINT NOT NULL DEFAULT 0,\n" +
                        "  `last_server` VARCHAR(64) NULL,\n" +
                        "  `last_world` VARCHAR(64) NULL,\n" +
                        "  `last_x` DOUBLE NULL,\n" +
                        "  `last_y` DOUBLE NULL,\n" +
                        "  `last_z` DOUBLE NULL,\n" +
                        "  `last_yaw` FLOAT NULL,\n" +
                        "  `last_pitch` FLOAT NULL,\n" +
                        "  `last_quit_at` BIGINT NOT NULL DEFAULT 0,\n" +
                        "  PRIMARY KEY (`uuid`, `date_key`),\n" +
                        "  INDEX `activity_daily_date_idx` (`date_key`),\n" +
                        "  INDEX `activity_daily_last_active_idx` (`last_active_at`)\n" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;");

        tables.put("ZonelyCorePlayerActivitySummary",
                "CREATE TABLE IF NOT EXISTS `ZonelyCorePlayerActivitySummary` (\n" +
                        "  `uuid` VARCHAR(36) NOT NULL,\n" +
                        "  `name` VARCHAR(64) NOT NULL,\n" +
                        "  `last_server` VARCHAR(64) NULL,\n" +
                        "  `last_world` VARCHAR(64) NULL,\n" +
                        "  `last_x` DOUBLE NULL,\n" +
                        "  `last_y` DOUBLE NULL,\n" +
                        "  `last_z` DOUBLE NULL,\n" +
                        "  `last_yaw` FLOAT NULL,\n" +
                        "  `last_pitch` FLOAT NULL,\n" +
                        "  `last_seen_at` BIGINT NOT NULL DEFAULT 0,\n" +
                        "  `last_join_at` BIGINT NOT NULL DEFAULT 0,\n" +
                        "  `last_quit_at` BIGINT NOT NULL DEFAULT 0,\n" +
                        "  `last_chat_message` VARCHAR(512) NULL,\n" +
                        "  `last_command` VARCHAR(256) NULL,\n" +
                        "  `total_messages` BIGINT UNSIGNED NOT NULL DEFAULT 0,\n" +
                        "  `total_commands` BIGINT UNSIGNED NOT NULL DEFAULT 0,\n" +
                        "  `updated_at` BIGINT NOT NULL DEFAULT 0,\n" +
                        "  PRIMARY KEY (`uuid`),\n" +
                        "  INDEX `activity_summary_name_idx` (`name`),\n" +
                        "  INDEX `activity_summary_seen_idx` (`last_seen_at`)\n" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;");

        tables.put("ZonelyCorePlayerActivityRecent",
                "CREATE TABLE IF NOT EXISTS `ZonelyCorePlayerActivityRecent` (\n" +
                        "  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,\n" +
                        "  `uuid` VARCHAR(36) NOT NULL,\n" +
                        "  `name` VARCHAR(64) NOT NULL,\n" +
                        "  `server` VARCHAR(64) NOT NULL,\n" +
                        "  `event_type` VARCHAR(16) NOT NULL,\n" +
                        "  `content` VARCHAR(512) NULL,\n" +
                        "  `world` VARCHAR(64) NULL,\n" +
                        "  `x` DOUBLE NULL,\n" +
                        "  `y` DOUBLE NULL,\n" +
                        "  `z` DOUBLE NULL,\n" +
                        "  `yaw` FLOAT NULL,\n" +
                        "  `pitch` FLOAT NULL,\n" +
                        "  `created_at` BIGINT NOT NULL,\n" +
                        "  PRIMARY KEY (`id`),\n" +
                        "  INDEX `activity_recent_uuid_type_idx` (`uuid`, `event_type`, `created_at`),\n" +
                        "  INDEX `activity_recent_created_idx` (`created_at`)\n" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;");

        TABLES = Collections.unmodifiableMap(tables);
    }

    private ChatBridgeSchema() {
    }

    public static Map<String, String> tables() {
        return TABLES;
    }
}
