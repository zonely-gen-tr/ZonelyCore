package dev.zonely.whiteeffect.clubs;

import dev.zonely.whiteeffect.clubs.model.ClubData;
import dev.zonely.whiteeffect.clubs.model.ClubDraft;
import dev.zonely.whiteeffect.clubs.model.ClubMember;
import dev.zonely.whiteeffect.clubs.model.ClubMemberRole;
import dev.zonely.whiteeffect.database.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ClubRepository {

    private final Logger logger;

    public ClubRepository(Logger logger) {
        this.logger = logger;
    }

    public Optional<ClubData> findByOwner(UUID ownerUuid) {
        String sql = "SELECT * FROM es_clubs WHERE owner_uuid = ? LIMIT 1";
        return queryClub(sql, stmt -> stmt.setString(1, ownerUuid.toString()));
    }

    public Optional<ClubData> findById(int clubId) {
        String sql = "SELECT * FROM es_clubs WHERE id = ? LIMIT 1";
        return queryClub(sql, stmt -> stmt.setInt(1, clubId));
    }

    public Optional<ClubData> findByMember(UUID uniqueId) {
        String sql = "SELECT c.* FROM es_clubs c " +
                "INNER JOIN es_club_members m ON m.club_id = c.id " +
                "WHERE m.member_uuid = ? LIMIT 1";
        return queryClub(sql, stmt -> stmt.setString(1, uniqueId.toString()));
    }

    public int insertClub(ClubDraft draft) {
        String sql = "INSERT INTO es_clubs (name, tag, owner_uuid, owner_name, primary_color, banner_json) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection connection = Database.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, draft.getName());
            statement.setString(2, draft.getTag());
            statement.setString(3, draft.getOwnerUuid().toString());
            statement.setString(4, draft.getOwnerName());
            statement.setString(5, draft.getColorHex());
            statement.setString(6, draft.getBannerJson());
            statement.executeUpdate();
            try (ResultSet rs = statement.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "[Clubs] Failed to insert club.", ex);
        }
        return -1;
    }

    public boolean insertMember(int clubId, UUID uuid, String username, ClubMemberRole role) {
        String sql = "INSERT INTO es_club_members (club_id, member_uuid, member_name, role) VALUES (?, ?, ?, ?)";
        try (Connection connection = Database.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, clubId);
            statement.setString(2, uuid.toString());
            statement.setString(3, username);
            statement.setString(4, role.name());
            return statement.executeUpdate() > 0;
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "[Clubs] Failed to insert member.", ex);
            return false;
        }
    }

    public List<ClubMember> listMembers(int clubId) {
        String sql = "SELECT * FROM es_club_members WHERE club_id = ? ORDER BY role ASC, member_name ASC";
        List<ClubMember> members = new ArrayList<>();
        try (Connection connection = Database.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, clubId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    members.add(mapMember(rs));
                }
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "[Clubs] Failed to list members.", ex);
        }
        return members;
    }

    public boolean existsByTag(String tag) {
        String sql = "SELECT 1 FROM es_clubs WHERE tag = ? LIMIT 1";
        try (Connection connection = Database.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tag);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "[Clubs] Tag lookup failed.", ex);
            return true;
        }
    }

    public boolean removeMember(int clubId, UUID uuid) {
        String sql = "DELETE FROM es_club_members WHERE club_id = ? AND member_uuid = ? LIMIT 1";
        try (Connection connection = Database.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, clubId);
            statement.setString(2, uuid.toString());
            return statement.executeUpdate() > 0;
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "[Clubs] Failed to remove member.", ex);
            return false;
        }
    }

    public boolean updateColor(int clubId, String colorHex) {
        String sql = "UPDATE es_clubs SET primary_color = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? LIMIT 1";
        try (Connection connection = Database.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, colorHex);
            statement.setInt(2, clubId);
            return statement.executeUpdate() > 0;
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "[Clubs] Failed to update club color.", ex);
            return false;
        }
    }

    public boolean updateBanner(int clubId, String bannerJson) {
        String sql = "UPDATE es_clubs SET banner_json = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? LIMIT 1";
        try (Connection connection = Database.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, bannerJson);
            statement.setInt(2, clubId);
            return statement.executeUpdate() > 0;
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "[Clubs] Failed to update banner.", ex);
            return false;
        }
    }

    public List<ClubData> listAll() {
        String sql = "SELECT * FROM es_clubs ORDER BY created_at ASC";
        List<ClubData> clubs = new ArrayList<>();
        try (Connection connection = Database.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                clubs.add(mapClub(rs));
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "[Clubs] Failed to list clubs.", ex);
        }
        return clubs;
    }

    private Optional<ClubData> queryClub(String sql, StatementConfigurer configurer) {
        try (Connection connection = Database.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            configurer.configure(statement);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapClub(rs));
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "[Clubs] Query failed.", ex);
            return Optional.empty();
        }
    }

    private ClubData mapClub(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String name = rs.getString("name");
        String tag = rs.getString("tag");
        UUID ownerUuid = UUID.fromString(rs.getString("owner_uuid"));
        String ownerName = rs.getString("owner_name");
        String colorHex = rs.getString("primary_color");
        String bannerJson = rs.getString("banner_json");
        Instant createdAt = rs.getTimestamp("created_at").toInstant();
        List<ClubMember> members = listMembers(id);
        return new ClubData(id, name, tag, ownerUuid, ownerName, colorHex, bannerJson, createdAt, members);
    }

    private ClubMember mapMember(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        int clubId = rs.getInt("club_id");
        UUID uuid = UUID.fromString(rs.getString("member_uuid"));
        String username = rs.getString("member_name");
        ClubMemberRole role = ClubMemberRole.valueOf(rs.getString("role"));
        java.sql.Timestamp joined = rs.getTimestamp("joined_at");
        Instant joinedAt = joined != null ? joined.toInstant() : Instant.now();
        return new ClubMember(id, clubId, uuid, username, role, joinedAt);
    }

    private interface StatementConfigurer {
        void configure(PreparedStatement statement) throws SQLException;
    }
}
