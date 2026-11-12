package io.clueterra.api.friends.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import io.clueterra.api.common.utils.loader.SqlLoader;
import io.hypersistence.tsid.TSID;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class FriendsJdbcRepository {

    private static final String SEARCH_USERS = SqlLoader.load(FriendsJdbcRepository.class, "searchUsers");
    private static final String INSERT_FRIEND_REQUEST = SqlLoader.load(FriendsJdbcRepository.class, "insertFriendRequest");
    private static final String ACCEPT_FRIEND_REQUEST = SqlLoader.load(FriendsJdbcRepository.class, "acceptFriendRequest");
    private static final String DELETE_FRIEND_REQUEST = SqlLoader.load(FriendsJdbcRepository.class, "deleteFriendRequest");
    private static final String DELETE_FRIENDSHIP = SqlLoader.load(FriendsJdbcRepository.class, "deleteFriendship");
    private static final String FIND_FRIENDS = SqlLoader.load(FriendsJdbcRepository.class, "findFriends");
    private static final String FIND_INCOMING_REQUESTS = SqlLoader.load(FriendsJdbcRepository.class, "findIncomingRequests");
    private static final String FIND_OUTGOING_REQUESTS = SqlLoader.load(FriendsJdbcRepository.class, "findOutgoingRequests");
    private static final String FIND_FRIENDS_LEADERBOARD_WEEK =
            SqlLoader.load(FriendsJdbcRepository.class, "findFriendsLeaderboardWeek");
    private static final String FIND_FRIENDS_LEADERBOARD_ALL_TIME =
            SqlLoader.load(FriendsJdbcRepository.class, "findFriendsLeaderboardAllTime");

    private static final String COUNT_ACCEPTED_FRIENDS = """
            SELECT COUNT(*)::INTEGER
            FROM user_friendships
            WHERE status = 'ACCEPTED'
              AND (user_low_id = :userId OR user_high_id = :userId)
            """;

    private static final String COUNT_OUTGOING_PENDING = """
            SELECT COUNT(*)::INTEGER
            FROM user_friendships
            WHERE requested_by = :userId
              AND status = 'PENDING'
            """;

    private static final String EXISTS_ELIGIBLE_USER = """
            SELECT EXISTS (
                SELECT 1
                FROM users
                WHERE id = :userId
                  AND enabled = TRUE
                  AND is_guest = FALSE
            )
            """;

    private static final String BLOCK_USER = """
            INSERT INTO user_blocks (blocker_id, blocked_id, created_at)
            VALUES (:userId, :blockedUserId, NOW())
            ON CONFLICT (blocker_id, blocked_id) DO NOTHING
            """;

    private static final String UNBLOCK_USER = """
            DELETE FROM user_blocks
            WHERE blocker_id = :userId AND blocked_id = :blockedUserId
            """;

    private static final String DELETE_FRIENDSHIP_FOR_BLOCK = """
            DELETE FROM user_friendships
            WHERE user_low_id = LEAST(:userId, :blockedUserId)
              AND user_high_id = GREATEST(:userId, :blockedUserId)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public List<FriendUserRow> searchUsers(Long userId, String query, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("query", "%" + query.toLowerCase() + "%")
                .addValue("limit", limit);
        return jdbc.query(SEARCH_USERS, params, (rs, rowNum) -> mapUser(rs));
    }

    public Optional<Long> insertFriendRequest(Long userId, Long targetUserId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", TSID.Factory.getTsid().toLong())
                .addValue("userId", userId)
                .addValue("targetUserId", targetUserId);
        return jdbc.query(INSERT_FRIEND_REQUEST, params, (rs, rowNum) -> rs.getLong("id"))
                .stream()
                .findFirst();
    }

    public boolean acceptFriendRequest(Long userId, Long requestId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("requestId", requestId);
        return !jdbc.query(ACCEPT_FRIEND_REQUEST, params, (rs, rowNum) -> rs.getLong("id")).isEmpty();
    }

    public int deleteFriendRequest(Long userId, Long requestId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("requestId", requestId);
        return jdbc.update(DELETE_FRIEND_REQUEST, params);
    }

    public int deleteFriendship(Long userId, Long friendId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("friendId", friendId);
        return jdbc.update(DELETE_FRIENDSHIP, params);
    }

    public List<FriendRow> findFriends(Long userId) {
        return jdbc.query(FIND_FRIENDS, new MapSqlParameterSource("userId", userId), (rs, rowNum) ->
                new FriendRow(
                        rs.getLong("friendship_id"),
                        mapUser(rs),
                        toInstant(rs, "friends_since")));
    }

    public List<FriendRequestRow> findIncomingRequests(Long userId) {
        return findRequests(FIND_INCOMING_REQUESTS, userId);
    }

    public List<FriendRequestRow> findOutgoingRequests(Long userId) {
        return findRequests(FIND_OUTGOING_REQUESTS, userId);
    }

    public int countAcceptedFriends(Long userId) {
        return queryCount(COUNT_ACCEPTED_FRIENDS, new MapSqlParameterSource("userId", userId));
    }

    public int countOutgoingPending(Long userId) {
        return queryCount(COUNT_OUTGOING_PENDING, new MapSqlParameterSource("userId", userId));
    }

    public boolean isEligibleRegisteredUser(Long userId) {
        Boolean exists = jdbc.queryForObject(
                EXISTS_ELIGIBLE_USER,
                new MapSqlParameterSource("userId", userId),
                Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    public void blockUser(Long userId, Long blockedUserId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("blockedUserId", blockedUserId);
        jdbc.update(DELETE_FRIENDSHIP_FOR_BLOCK, params);
        jdbc.update(BLOCK_USER, params);
    }

    public int unblockUser(Long userId, Long blockedUserId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("blockedUserId", blockedUserId);
        return jdbc.update(UNBLOCK_USER, params);
    }

    public List<FriendLeaderboardEntryRow> findFriendsLeaderboard(Long userId, String period, LocalDate weekStart) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("weekStart", weekStart);
        String sql = "alltime".equalsIgnoreCase(period) ? FIND_FRIENDS_LEADERBOARD_ALL_TIME : FIND_FRIENDS_LEADERBOARD_WEEK;
        return jdbc.query(sql, params, (rs, rowNum) -> mapLeaderboardEntry(rs));
    }

    private List<FriendRequestRow> findRequests(String sql, Long userId) {
        return jdbc.query(sql, new MapSqlParameterSource("userId", userId), (rs, rowNum) ->
                new FriendRequestRow(
                        rs.getLong("request_id"),
                        mapUser(rs),
                        rs.getString("direction"),
                        toInstant(rs, "created_at")));
    }

    private int queryCount(String sql, MapSqlParameterSource params) {
        Integer count = jdbc.queryForObject(sql, params, Integer.class);
        return count != null ? count : 0;
    }

    private FriendUserRow mapUser(ResultSet rs) throws SQLException {
        return new FriendUserRow(
                rs.getLong("user_id"),
                rs.getString("username"),
                rs.getString("display_name"),
                rs.getString("avatar_url"),
                rs.getLong("total_xp"),
                rs.getInt("level"));
    }

    private FriendLeaderboardEntryRow mapLeaderboardEntry(ResultSet rs) throws SQLException {
        FriendUserRow user = mapUser(rs);
        return new FriendLeaderboardEntryRow(
                rs.getInt("rank"),
                user,
                rs.getLong("score_xp"),
                rs.getLong("total_xp"),
                rs.getInt("level"),
                rs.getBoolean("current_user"));
    }

    private Instant toInstant(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column).toInstant();
    }
}
