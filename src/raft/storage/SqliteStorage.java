/**
 * Project   : Distributed Consensus Simulator (Raft-lite)
 * Author    : Shivansh Kumar (AP25110010113)
 * Section   : E | 2nd Year, 3rd Semester | SRM University AP
 * Portfolio : shivanshonline.in
 */
package raft.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import raft.core.LogEntry;
import raft.exception.StorageException;

/**
 * Stores node state and logs in a SQLite database using JDBC (Unit 4).
 * Needs the sqlite-jdbc jar inside the lib/ folder. If the jar is missing the
 * constructor throws a StorageException and the program falls back to files.
 *
 * Tables:
 *   node_state(node_id, current_term, voted_for)
 *   raft_log(node_id, log_index, term, command)
 *
 * CRUD operations used: CREATE TABLE / INSERT, SELECT, UPDATE, DELETE.
 */
public class SqliteStorage implements Storage {
    private final Connection connection;
    private final String path;

    public SqliteStorage(String dbPath) {
        this.path = dbPath;
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException | LinkageError e) {
            // LinkageError: the jar exists but one of its own dependencies is missing
            throw new StorageException("sqlite-jdbc driver not usable (put the jar in lib/): " + e, e);
        }
        try {
            File parent = new File(dbPath).getAbsoluteFile().getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA synchronous=OFF");
                st.execute("CREATE TABLE IF NOT EXISTS node_state ("
                        + "node_id INTEGER PRIMARY KEY, "
                        + "current_term INTEGER NOT NULL, "
                        + "voted_for INTEGER NOT NULL)");
                st.execute("CREATE TABLE IF NOT EXISTS raft_log ("
                        + "node_id INTEGER NOT NULL, "
                        + "log_index INTEGER NOT NULL, "
                        + "term INTEGER NOT NULL, "
                        + "command TEXT NOT NULL, "
                        + "PRIMARY KEY (node_id, log_index))");
            }
        } catch (SQLException e) {
            throw new StorageException("Cannot open SQLite database " + dbPath, e);
        }
    }

    @Override
    public synchronized void saveState(int nodeId, long currentTerm, int votedFor) {
        try {
            int updated;
            try (PreparedStatement up = connection.prepareStatement(
                    "UPDATE node_state SET current_term = ?, voted_for = ? WHERE node_id = ?")) {
                up.setLong(1, currentTerm);
                up.setInt(2, votedFor);
                up.setInt(3, nodeId);
                updated = up.executeUpdate();
            }
            if (updated == 0) {
                try (PreparedStatement ins = connection.prepareStatement(
                        "INSERT INTO node_state (node_id, current_term, voted_for) VALUES (?, ?, ?)")) {
                    ins.setInt(1, nodeId);
                    ins.setLong(2, currentTerm);
                    ins.setInt(3, votedFor);
                    ins.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Cannot save state of node " + nodeId, e);
        }
    }

    @Override
    public synchronized PersistentState loadState(int nodeId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT current_term, voted_for FROM node_state WHERE node_id = ?")) {
            ps.setInt(1, nodeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new PersistentState(rs.getLong(1), rs.getInt(2));
                }
                return null;
            }
        } catch (SQLException e) {
            throw new StorageException("Cannot load state of node " + nodeId, e);
        }
    }

    @Override
    public synchronized void saveLog(int nodeId, List<LogEntry<String>> entries) {
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement del = connection.prepareStatement(
                    "DELETE FROM raft_log WHERE node_id = ?");
                 PreparedStatement ins = connection.prepareStatement(
                         "INSERT INTO raft_log (node_id, log_index, term, command) VALUES (?, ?, ?, ?)")) {
                del.setInt(1, nodeId);
                del.executeUpdate();
                for (LogEntry<String> e : entries) {
                    ins.setInt(1, nodeId);
                    ins.setInt(2, e.getIndex());
                    ins.setLong(3, e.getTerm());
                    ins.setString(4, e.getCommand());
                    ins.addBatch();
                }
                ins.executeBatch();
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new StorageException("Cannot save log of node " + nodeId, e);
        }
    }

    @Override
    public synchronized List<LogEntry<String>> loadLog(int nodeId) {
        List<LogEntry<String>> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT log_index, term, command FROM raft_log WHERE node_id = ? ORDER BY log_index")) {
            ps.setInt(1, nodeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new LogEntry<>(rs.getInt(1), rs.getLong(2), rs.getString(3)));
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Cannot load log of node " + nodeId, e);
        }
        return result;
    }

    @Override
    public synchronized void clear() {
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("DELETE FROM raft_log");
            st.executeUpdate("DELETE FROM node_state");
        } catch (SQLException e) {
            throw new StorageException("Cannot clear database", e);
        }
    }

    @Override
    public String name() {
        return "sqlite (" + path + ")";
    }

    @Override
    public synchronized void close() {
        try {
            connection.close();
        } catch (SQLException ignored) {
            // nothing useful to do while shutting down
        }
    }
}
