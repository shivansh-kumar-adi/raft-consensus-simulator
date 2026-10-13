Optional: put the SQLite JDBC driver jar here (e.g. sqlite-jdbc-3.41.2.2.jar).
Download it from https://github.com/xerial/sqlite-jdbc/releases  (or Maven Central).

With the jar present the simulator stores logs in data/raft.db using JDBC.
Without it the program still works and automatically saves to plain files instead.
Newer driver versions may also need the slf4j-api jar placed here.
