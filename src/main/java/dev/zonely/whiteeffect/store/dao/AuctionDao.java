package dev.zonely.whiteeffect.store.dao;

import dev.zonely.whiteeffect.database.Database;
import dev.zonely.whiteeffect.store.model.AuctionItem;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AuctionDao {
    private final int serverId;

    public AuctionDao(int serverId) {
        this.serverId = serverId;
    }

    public static class MenuData {
        private final List<AuctionItem> items;
        private final int credit;
        public MenuData(List<AuctionItem> items, int credit) {
            this.items  = items;
            this.credit = credit;
        }
        public List<AuctionItem> getItems() { return items; }
        public int getCredit()          { return credit; }
    }

    public MenuData loadMenuData(String playerName) throws SQLException {
        String sqlUser =
    "SELECT id AS userid, credit " +
    "FROM userslist " +
    "WHERE nick = ?";
        String sqlAuctions =
            "SELECT * FROM auction_items " +
            "WHERE approved = 1 AND buyer_user_id IS NULL " +
            "ORDER BY created_at DESC";

        try (Connection c = Database.getInstance().getConnection()) {
            int userId = 0, credit = 0;
            try (PreparedStatement ps = c.prepareStatement(sqlUser)) {
                ps.setString(1, playerName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        userId = rs.getInt("userid");
                        credit = rs.getInt("credit");
                    }
                }
            }

            List<AuctionItem> list = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(sqlAuctions);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }

            return new MenuData(list, credit);
        }
    }

    private AuctionItem mapRow(ResultSet rs) throws SQLException {
        AuctionItem ai = new AuctionItem();
        ai.setId(rs.getInt("id"));
        ai.setRowOrder((Integer) rs.getObject("row_order"));
        ai.setServerId(rs.getInt("server_id"));
        ai.setCreatorId(rs.getInt("creator_id"));
        ai.setUserId(rs.getInt("user_id"));
        ai.setMaterial(rs.getString("material"));
        ai.setCustomTitle(rs.getString("custom_title"));
        ai.setDescription(rs.getString("description"));
        ai.setEnchantments(rs.getString("enchantments"));
        ai.setDurabilityCurrent(rs.getInt("durability_current"));
        ai.setDurabilityMax(rs.getInt("durability_max"));
        ai.setItemData(rs.getString("item_data"));
        ai.setCreditCost(rs.getInt("credit_cost"));
        ai.setApproved(rs.getBoolean("approved"));
        ai.setBuyerName(rs.getString("buyer_name"));
        ai.setBuyerUserId(rs.getInt("buyer_user_id"));
        ai.setCreatedAt(rs.getTimestamp("created_at"));
        return ai;
    }

    public enum PurchaseStatus {
        SUCCESS,
        INSUFFICIENT_FUNDS,
        BUYER_NOT_FOUND,
        CREATOR_NOT_FOUND
    }

    public static class PurchaseResult {
        private final PurchaseStatus status;
        private final int buyerCredit;
        private final int creatorCredit;

        public PurchaseResult(PurchaseStatus status, int buyerCredit, int creatorCredit) {
            this.status = status;
            this.buyerCredit = buyerCredit;
            this.creatorCredit = creatorCredit;
        }

        public PurchaseStatus getStatus() {
            return status;
        }

        public int getBuyerCredit() {
            return buyerCredit;
        }

        public int getCreatorCredit() {
            return creatorCredit;
        }
    }

    public int insert(AuctionItem it) throws SQLException {
        String sql = "INSERT INTO auction_items " +
            "(row_order, server_id, creator_id, user_id, material, custom_title, description, " +
            " enchantments, durability_current, durability_max, item_data, credit_cost, approved, created_at) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?, 0, NOW())";
        try (Connection c = Database.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setObject(1, it.getRowOrder());
            ps.setInt(2, serverId);
            ps.setInt(3, it.getCreatorId());
            ps.setInt(4, it.getUserId());
            ps.setString(5, it.getMaterial());
            ps.setString(6, it.getCustomTitle());
            ps.setString(7, it.getDescription());
            ps.setString(8, it.getEnchantments());
            ps.setInt(9, it.getDurabilityCurrent());
            ps.setInt(10, it.getDurabilityMax());
            ps.setString(11, it.getItemData());
            ps.setInt(12, it.getCreditCost());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    public AuctionItem getById(int id) throws SQLException {
        String sql = "SELECT * FROM auction_items WHERE id = ?";
        try (Connection c = Database.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public void markApproved(int id) throws SQLException {
        String sql = "UPDATE auction_items SET approved = 1 WHERE id = ?";
        try (Connection c = Database.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    public void markAsSold(int id, String buyerName) throws SQLException {
        String sql = "UPDATE auction_items " +
            "SET buyer_name = ?, buyer_user_id = ?, approved = 0 " +
            "WHERE id = ?";
        try (Connection c = Database.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, buyerName);
            ps.setInt(2, fetchUserId(buyerName));
            ps.setInt(3, id);
            ps.executeUpdate();
        }
    }

    public List<AuctionItem> listByCreator(int creatorId) throws SQLException {
        String sql = "SELECT * FROM auction_items WHERE creator_id = ? ORDER BY created_at DESC";
        List<AuctionItem> list = new ArrayList<>();
        try (Connection c = Database.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, creatorId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    public List<AuctionItem> listByBuyer(int buyerId) throws SQLException {
        String sql = "SELECT * FROM auction_items WHERE buyer_user_id = ? ORDER BY created_at DESC";
        List<AuctionItem> list = new ArrayList<>();
        try (Connection c = Database.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, buyerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    public int fetchUserId(String playerName) throws SQLException {
    String sql = "SELECT id FROM userslist WHERE nick = ?";
    try (Connection c = Database.getInstance().getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {
        ps.setString(1, playerName);
        try (ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt("id") : 0;
        }
    }
}

    public int getCredit(int userId) throws SQLException {
        String sql = "SELECT credit FROM userslist WHERE id = ?";
        try (Connection c = Database.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("credit") : 0;
            }
        }
    }

    public void updateCredit(int userId, int newCredit) throws SQLException {
        String sql = "UPDATE userslist SET credit = ? WHERE id = ?";
        try (Connection c = Database.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, newCredit);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    public PurchaseResult finalizePurchase(AuctionItem item, int buyerId, String buyerName) throws SQLException {
        String lockSql = "SELECT credit FROM userslist WHERE id = ? FOR UPDATE";
        String updateSql = "UPDATE userslist SET credit = ? WHERE id = ?";
        String markSoldSql = "UPDATE auction_items SET buyer_name = ?, buyer_user_id = ?, approved = 0 WHERE id = ?";

        try (Connection conn = Database.getInstance().getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                Integer buyerCredit = selectCreditForUpdate(conn, lockSql, buyerId);
                if (buyerCredit == null) {
                    conn.rollback();
                    return new PurchaseResult(PurchaseStatus.BUYER_NOT_FOUND, 0, 0);
                }
                if (buyerCredit < item.getCreditCost()) {
                    conn.rollback();
                    return new PurchaseResult(PurchaseStatus.INSUFFICIENT_FUNDS, buyerCredit, 0);
                }

                Integer creatorCredit = selectCreditForUpdate(conn, lockSql, item.getCreatorId());
                if (creatorCredit == null) {
                    conn.rollback();
                    return new PurchaseResult(PurchaseStatus.CREATOR_NOT_FOUND, buyerCredit, 0);
                }

                int updatedBuyerCredit = buyerCredit - item.getCreditCost();
                int updatedCreatorCredit = creatorCredit + item.getCreditCost();

                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setInt(1, updatedBuyerCredit);
                    ps.setInt(2, buyerId);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setInt(1, updatedCreatorCredit);
                    ps.setInt(2, item.getCreatorId());
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement(markSoldSql)) {
                    ps.setString(1, buyerName);
                    ps.setInt(2, buyerId);
                    ps.setInt(3, item.getId());
                    ps.executeUpdate();
                }

                conn.commit();
                return new PurchaseResult(PurchaseStatus.SUCCESS, updatedBuyerCredit, updatedCreatorCredit);
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        }
    }

    private Integer selectCreditForUpdate(Connection conn, String sql, int userId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("credit");
                }
                return null;
            }
        }
    }

    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM auction_items WHERE id = ?";
        try (Connection c = Database.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }
}
