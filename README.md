# Distributed Consensus Simulator (Raft-lite)

[![CI Build](https://github.com/shivansh07adi-cloud/raft-lite/actions/workflows/ci.yml/badge.svg)](https://github.com/shivansh07adi-cloud/raft-lite/actions/workflows/ci.yml)
[![Java 17+](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://adoptium.net/)
[![Pure Java](https://img.shields.io/badge/Dependencies-none%20(pure%20JDK)-brightgreen.svg)](#requirements)
[![Tests](https://img.shields.io/badge/Tests-34%20checks%20passing-success.svg)](#testing)
[![GUI](https://img.shields.io/badge/GUI-Swing%20%2F%20AWT-blue.svg)](#using-the-gui)
[![Storage](https://img.shields.io/badge/Storage-SQLite%20(JDBC)%20%7C%20Files-lightgrey.svg)](#persistence-layer)

A Java simulator of **distributed nodes** that demonstrates **leader election, log replication, consensus and fault tolerance** using a simplified version of the **Raft** algorithm. Every node runs in its own thread, talks to the others through a simulated lossy network, survives crashes and network partitions, and saves its state through a JDBC/SQLite persistence layer.

It is a focused project, but it is built the way a real system would be: a clean package structure, an abstract message hierarchy, interface-based storage back-ends, user-defined exceptions, thread-safe state, a live Swing dashboard, a scripted demo, and an automated test suite that continuously checks Raft's central safety property.

| | |
|---|---|
| **Author** | Shivansh Kumar |
| **Registration No.** | AP25110010113 |
| **Section** | E, 2nd Year, 3rd Semester |
| **University** | SRM University AP, Department of CSE |
| **Course** | Advance Java Programming - CSE 309 |
| **Portfolio** | [shivanshonline.in](https://shivanshonline.in) |

<p align="center">
  <img src="docs/images/cluster-healthy.png" width="48%" alt="Healthy 5-node cluster with one leader">
  <img src="docs/images/cluster-leader-crash.png" width="48%" alt="After the leader and another node crashed, a new leader was elected">
</p>
<p align="center"><em>Cluster view of the dashboard. Left: healthy cluster (N1 is leader). Right: N1 and N2 crashed, N4 was elected leader of term 2 and the dashed red lines show unreachable nodes.</em></p>

---

## Table of contents

- [Why this project](#why-this-project)
- [Features](#features)
- [System architecture](#system-architecture)
- [Class design](#class-design)
- [Node state machine](#node-state-machine)
- [How it works](#how-it-works)
  - [Node thread loop](#node-thread-loop)
  - [Leader election](#leader-election)
  - [Log replication and commit](#log-replication-and-commit)
  - [Follower log-matching decision](#follower-log-matching-decision)
  - [Crash and recovery](#crash-and-recovery)
  - [Network partition scenario](#network-partition-scenario)
- [Simulated network](#simulated-network)
- [Persistence layer](#persistence-layer)
- [Threading and synchronization model](#threading-and-synchronization-model)
- [Requirements](#requirements)
- [Quick start](#quick-start)
- [Command line options](#command-line-options)
- [Using the GUI](#using-the-gui)
- [Console commands](#console-commands)
- [Scripted demo](#scripted-demo)
- [Testing](#testing)
- [Configuration](#configuration)
- [Project layout](#project-layout)
- [Syllabus mapping](#syllabus-mapping)
- [Design decisions and limitations](#design-decisions-and-limitations)
- [Future work](#future-work)
- [Upload to GitHub](#upload-to-github)
- [Author](#author)

---

## Why this project

Consensus is how a group of computers agrees on one value even when some of them crash or the network breaks. It is the foundation of systems such as **etcd** (which powers Kubernetes), **Consul** and many distributed databases. Raft is a consensus algorithm designed to be understandable. This project re-implements its core ideas from scratch so the behaviour can be **seen, controlled and tested**:

> *Break the leader. Cut the network in half. Drop 30% of the messages. The cluster still agrees on one history.*

## Features

| Area | What is implemented |
|---|---|
| **Leader election** | Randomized election timeouts, terms, one vote per term, majority voting, log up-to-date check |
| **Log replication** | `AppendEntries` with consistency check, conflict repair, batching, commit on majority, no-op entry for new leaders |
| **Consensus** | Every node applies committed commands, in order, to a key-value state machine |
| **Fault tolerance** | Crash and recover any node, crash the leader, no-quorum handling |
| **Network faults** | Partitions (any grouping), random message loss, random delay, message tracing |
| **Persistence** | Term, vote and log saved through `Storage` (SQLite via JDBC, plain files, or memory) and reloaded on recovery |
| **Interfaces** | Swing dashboard, interactive console, scripted `--demo` |
| **Quality** | 8 test scenarios (34 checks) plus a live "never two leaders in one term" safety sampler, GitHub Actions CI |

## System architecture

```mermaid
flowchart TB
    User(["Person using the simulator"])

    subgraph UI["User interfaces (package raft.ui and raft.sim)"]
        direction LR
        GUI["SwingDashboard<br/>+ ClusterPanel"]
        CLI["ConsoleUI<br/>(Scanner commands)"]
        DEMO["DemoScenario<br/>(scripted --demo)"]
    end

    subgraph SIM["Simulation control (raft.sim)"]
        CL["Cluster<br/>start / stop, submit,<br/>crash / recover, partition, heal"]
    end

    subgraph CORE["Raft core (raft.core)"]
        direction LR
        N1["RaftNode 1<br/>(thread)"]
        N2["RaftNode 2<br/>(thread)"]
        N3["RaftNode 3<br/>(thread)"]
        NX["RaftNode n<br/>(thread)"]
    end

    subgraph NET["Simulated network (raft.net)"]
        NW["Network<br/>delay, random loss,<br/>partitions"]
    end

    subgraph STO["Persistence (raft.storage)"]
        direction LR
        SQL[("SqliteStorage<br/>JDBC")]
        FIL[("FileStorage<br/>java.io")]
        MEM[("MemoryStorage")]
    end

    SM["StateMachine<br/>(KeyValueStateMachine)"]
    EV["EventLog<br/>(shared, thread-safe)"]

    User --> GUI
    User --> CLI
    User --> DEMO
    GUI --> CL
    CLI --> CL
    DEMO --> CL

    CL --> N1
    CL --> N2
    CL --> N3
    CL --> NX
    CL -- "partition, loss, delay" --> NW

    N1 <--> NW
    N2 <--> NW
    N3 <--> NW
    NX <--> NW

    N1 --> SM
    N1 -- "save / load" --> STO
    N1 -. "log events" .-> EV
    NW -. "log events" .-> EV
    EV -. "live listener" .-> GUI
```

**Reading the diagram:** a person talks to one of three interfaces. They all use the single `Cluster` class, which owns the node threads and the network. Nodes never call each other directly: every message goes through `Network`, so faults can be injected in exactly one place. Each node writes its important state to a `Storage` back-end and applies committed commands to its own `StateMachine`.

## Class design

```mermaid
classDiagram
    direction LR

    class Message {
        <<abstract>>
        -int from
        -int to
        -long term
        +getType() MessageType
        +dispatch(MessageHandler handler)*
        +describe() String*
    }
    class RequestVote {
        -int lastLogIndex
        -long lastLogTerm
    }
    class VoteReply {
        -boolean granted
    }
    class AppendEntries {
        -int prevLogIndex
        -long prevLogTerm
        -List entries
        -int leaderCommit
    }
    class AppendReply {
        -boolean success
        -int matchIndex
    }
    Message <|-- RequestVote
    Message <|-- VoteReply
    Message <|-- AppendEntries
    Message <|-- AppendReply

    class MessageHandler {
        <<interface>>
        +onRequestVote(RequestVote)
        +onVoteReply(VoteReply)
        +onAppendEntries(AppendEntries)
        +onAppendReply(AppendReply)
    }
    class RaftNode {
        -long currentTerm
        -int votedFor
        -NodeState state
        -int commitIndex
        -int lastApplied
        +run()
        +submit(String command) int
        +crash()
        +recover()
        +snapshot() NodeSnapshot
    }
    MessageHandler <|.. RaftNode
    Message ..> MessageHandler : dispatches to

    class LogEntry~T~ {
        -int index
        -long term
        -T command
    }
    class ReplicatedLog~T~ {
        +append(term, command)
        +truncateFrom(index)
        +slice(from, max)
        +termAt(index)
    }
    ReplicatedLog~T~ o-- LogEntry~T~
    RaftNode *-- ReplicatedLog~T~

    class StateMachine {
        <<interface>>
        +apply(String command) String
        +reset()
        +snapshot() Map
    }
    class KeyValueStateMachine
    StateMachine <|.. KeyValueStateMachine
    RaftNode --> StateMachine

    class Storage {
        <<interface>>
        +saveState(id, term, votedFor)
        +loadState(id)
        +saveLog(id, entries)
        +loadLog(id)
        +clear()
    }
    class SqliteStorage
    class FileStorage
    class MemoryStorage
    Storage <|.. SqliteStorage
    Storage <|.. FileStorage
    Storage <|.. MemoryStorage
    RaftNode --> Storage

    class Network {
        +send(Message)
        +partition(groups)
        +heal()
        +setDropRate(rate)
    }
    RaftNode --> Network
    Network --> RaftNode : delivers

    class Cluster {
        +start()
        +submit(command)
        +crash(id)
        +recover(id)
        +findLeader()
    }
    Cluster o-- RaftNode
    Cluster *-- Network
```

Key object-oriented ideas: `Message` is an **abstract class** with four subclasses and uses **double dispatch** (`message.dispatch(node)` calls the right `onXxx` method, so the node needs no `if/else` on message types). `StateMachine`, `Storage` and `MessageHandler` are **interfaces** with interchangeable implementations. `LogEntry<T>` and `ReplicatedLog<T>` are **generic classes**.

## Node state machine

Every node is always in exactly one of three roles.

```mermaid
stateDiagram-v2
    [*] --> Follower : node starts or recovers

    Follower --> Candidate : election timeout, no heartbeat heard
    Candidate --> Candidate : election timeout again (split vote), new term
    Candidate --> Leader : votes from a majority
    Candidate --> Follower : sees a leader of the same term or a higher term
    Leader --> Follower : sees a higher term
    Follower --> Follower : valid AppendEntries resets the timer

    state "Crashed" as Crashed
    Follower --> Crashed : crash
    Candidate --> Crashed : crash
    Leader --> Crashed : crash
    Crashed --> Follower : recover, reload term, vote and log from storage
```

## How it works

### Node thread loop

Each `RaftNode` is a `Runnable` running in its own thread. The loop is tiny, and everything interesting happens in the handlers.

```mermaid
flowchart TD
    Start(["Thread starts"]) --> Reset["Reset election timer<br/>(random 1500 to 3000 ms in demo mode)"]
    Reset --> Run{"running?"}
    Run -- "no" --> Stop(["Thread ends"])
    Run -- "yes" --> Crashed{"crashed?"}
    Crashed -- "yes" --> Sleep["Sleep one tick<br/>and discard inbox"] --> Run
    Crashed -- "no" --> Poll["Wait up to 10 ms for a message<br/>in the inbox (BlockingQueue)"]
    Poll --> Lock["synchronized(this)"]
    Lock --> Got{"message<br/>received?"}
    Got -- "yes" --> Dispatch["message.dispatch(this)<br/>calls onRequestVote, onVoteReply,<br/>onAppendEntries or onAppendReply"]
    Got -- "no" --> Tick
    Dispatch --> Tick["tick()"]
    Tick --> Role{"role?"}
    Role -- "Leader" --> HB{"heartbeat<br/>interval passed?"}
    HB -- "yes" --> Send["broadcast AppendEntries<br/>to all followers"]
    HB -- "no" --> Run
    Send --> Run
    Role -- "Follower or Candidate" --> Timer{"election timer<br/>expired?"}
    Timer -- "yes" --> Elect["startElection()"]
    Timer -- "no" --> Run
    Elect --> Run
```

### Leader election

```mermaid
sequenceDiagram
    autonumber
    participant F1 as Node 1 (Follower)
    participant F2 as Node 2 (Follower)
    participant F3 as Node 3 (Follower)

    Note over F1,F3: Term 0. All nodes are followers with random election timers
    Note over F2: Timer of Node 2 expires first
    F2->>F2: become Candidate, term = 1, vote for self, save state
    F2->>F1: RequestVote (term 1, lastLogIndex, lastLogTerm)
    F2->>F3: RequestVote (term 1, lastLogIndex, lastLogTerm)
    F1->>F1: term is new and log is up to date, so grant and save vote
    F3->>F3: term is new and log is up to date, so grant and save vote
    F1-->>F2: VoteReply (term 1, granted)
    F3-->>F2: VoteReply (term 1, granted)
    F2->>F2: 3 of 3 votes is a majority, become Leader
    F2->>F2: append NOOP entry to the log
    F2->>F1: AppendEntries (heartbeat and NOOP)
    F2->>F3: AppendEntries (heartbeat and NOOP)
    F1->>F1: recognise Node 2 as leader, reset timer
    F3->>F3: recognise Node 2 as leader, reset timer
    Note over F1,F3: Followers keep their timers reset as long as heartbeats arrive
```

**Rules a node follows when asked for a vote** (`RaftNode.onRequestVote`):

```mermaid
flowchart TD
    A(["RequestVote arrives<br/>from candidate C, term T"]) --> B{"T greater than<br/>my current term?"}
    B -- "yes" --> C["Step down:<br/>term = T, votedFor = none, become Follower"]
    B -- "no" --> D
    C --> D{"T equals my term?"}
    D -- "no (T is old)" --> X["DENY"]
    D -- "yes" --> E{"Have I voted for someone<br/>else in this term?"}
    E -- "yes" --> X
    E -- "no" --> F{"Candidate log at least<br/>as up to date as mine?<br/>(higher last term wins,<br/>then longer log)"}
    F -- "no" --> X
    F -- "yes" --> G["GRANT:<br/>votedFor = C, save to storage,<br/>reset election timer"]
    X --> R(["Send VoteReply"])
    G --> R
```

> **Why random timeouts?** If every node timed out at the same moment they would all become candidates and split the votes forever. Randomization makes it very likely that one node times out first, collects a majority and sends heartbeats before the others even wake up.

### Log replication and commit

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant CL as Cluster
    participant L as Leader (Node 1)
    participant F2 as Follower (Node 2)
    participant F3 as Follower (Node 3)
    participant SM as State machine

    Client->>CL: submit("SET x 5")
    CL->>L: submit to the leader with the highest term
    L->>L: append entry (index 5, term 2) and save log
    par replicate to every follower
        L->>F2: AppendEntries (prev 4, entries [5], leaderCommit 4)
        L->>F3: AppendEntries (prev 4, entries [5], leaderCommit 4)
    end
    F2->>F2: prev entry matches, append entry 5, save log
    F2-->>L: AppendReply (success, matchIndex 5)
    L->>L: leader plus Node 2 is 2 of 3, a majority
    L->>L: entry is from the current term, so commitIndex = 5
    L->>SM: apply "SET x 5"
    F3->>F3: prev entry matches, append entry 5, save log
    F3-->>L: AppendReply (success, matchIndex 5)
    Note over L,F3: Next heartbeat carries leaderCommit = 5
    L->>F2: AppendEntries heartbeat (leaderCommit 5)
    L->>F3: AppendEntries heartbeat (leaderCommit 5)
    F2->>SM: commitIndex = 5, apply "SET x 5"
    F3->>SM: commitIndex = 5, apply "SET x 5"
```

An entry is **committed** once it is stored on a **majority** of nodes *and* belongs to the leader's current term. Committed entries are never lost, even if the leader crashes right afterwards, because any future leader must have them (see the vote rule above).

### Follower log-matching decision

This is the heart of Raft's safety: a follower only appends if its log agrees with the leader's at the previous index, otherwise the leader walks backwards until they agree. It is implemented in `RaftNode.onAppendEntries`.

```mermaid
flowchart TD
    A(["AppendEntries from leader<br/>term T, prevLogIndex, prevLogTerm, entries"]) --> B{"T less than my term?"}
    B -- "yes (stale leader)" --> R1["Reply FAILURE with my term<br/>(the old leader will step down)"]
    B -- "no" --> C{"T greater than my term?"}
    C -- "yes" --> D["Step down, adopt term T"]
    C -- "no" --> E
    D --> E["Become / stay Follower<br/>remember leader, reset election timer"]
    E --> F{"prevLogIndex greater than 0 and<br/>(log too short OR term at prevLogIndex differs)?"}
    F -- "yes" --> R2["Reply FAILURE with a hint<br/>(min of prev minus 1 and my last index)<br/>leader moves nextIndex back and retries"]
    F -- "no" --> G["For each new entry"]
    G --> H{"Entry index already<br/>in my log?"}
    H -- "no" --> I["Append it"]
    H -- "yes, same term" --> J["Keep it (already have it)"]
    H -- "yes, different term" --> K["CONFLICT: delete this entry and<br/>everything after it, then append"]
    I --> L
    J --> L
    K --> L{"More entries?"}
    L -- "yes" --> G
    L -- "no" --> M["Save log if it changed"]
    M --> N{"leaderCommit greater than<br/>my commitIndex?"}
    N -- "yes" --> O["commitIndex = min(leaderCommit, last verified index)<br/>apply newly committed entries"]
    N -- "no" --> P
    O --> P["Reply SUCCESS with matchIndex"]
```

### Crash and recovery

```mermaid
flowchart LR
    subgraph Crash["crash()"]
        direction TB
        C1["crashed = true"] --> C2["inbox cleared"]
        C2 --> C3["Network drops every message<br/>sent to this node"]
        C3 --> C4["Thread keeps running<br/>but does nothing"]
    end

    subgraph Recover["recover()"]
        direction TB
        R1["Reload currentTerm, votedFor<br/>and the log from Storage"] --> R2["state = Follower<br/>commitIndex = 0, lastApplied = 0"]
        R2 --> R3["State machine reset to empty"]
        R3 --> R4["Election timer restarted"]
        R4 --> R5["Leader sends missing entries<br/>and the current commitIndex"]
        R5 --> R6["Node re-applies the committed log<br/>and the state machine is rebuilt"]
    end

    Crash -- "Recover node / Recover all" --> Recover
```

What survives a crash and what does not:

| Survives (saved in storage) | Lost (rebuilt after restart) |
|---|---|
| `currentTerm` | role (always restarts as Follower) |
| `votedFor` (prevents voting twice in a term) | `commitIndex`, `lastApplied` |
| the complete log | state machine contents (rebuilt by replaying the log) |
| | leader-only data (`nextIndex`, `matchIndex`) |

### Network partition scenario

This is exactly what `STEP 5` and `STEP 6` of the [scripted demo](#scripted-demo) do with five nodes: the old leader N5 and N1 are cut off from N2, N3, N4.

```mermaid
sequenceDiagram
    autonumber
    participant M as Minority: N5 (old leader) and N1
    participant J as Majority: N2, N3, N4
    actor Client

    Note over M,J: Healthy cluster, N5 is leader of term 2
    Note over M,J: Network partition: {N1, N5} and {N2, N3, N4}
    Client->>M: SET stale 1 (sent to the isolated old leader)
    M->>M: N5 appends it at index 6 but can only reach 2 of 5 nodes
    M--xJ: AppendEntries lost (partition)
    Note over M: Entry 6 stays UNCOMMITTED, it can never reach a majority
    Note over J: No heartbeat arrives, election timeout
    J->>J: N2 becomes candidate, term 3, wins 3 of 5 votes
    Client->>J: SET w 99
    J->>J: N2 commits it on 3 of 5 nodes
    Note over M,J: Partition healed
    J->>M: AppendEntries (term 3)
    M->>M: N5 sees higher term, steps down to Follower
    M->>M: log conflict at index 6, delete the stale entry
    M->>M: copy the leader's log and apply it
    Note over M,J: All five nodes hold the same history. "stale" never reached the state machine
```

## Simulated network

All traffic goes through `Network`, so faults are injected in exactly one place and nodes cannot "cheat" by calling each other directly.

```mermaid
flowchart TD
    S(["node calls network.send(message)"]) --> P1{"Link cut by<br/>a partition?"}
    P1 -- "yes" --> D1["DROP<br/>(NetworkPartitionException<br/>caught and counted)"]
    P1 -- "no" --> L{"random number<br/>less than drop rate?"}
    L -- "yes" --> D2["DROP (random loss)"]
    L -- "no" --> W["Pick a random delay<br/>between min and max ms<br/>and schedule delivery"]
    W --> T["... delay passes ..."]
    T --> P2{"Partition created<br/>while in flight?"}
    P2 -- "yes" --> D3["DROP"]
    P2 -- "no" --> C{"Target node<br/>crashed?"}
    C -- "yes" --> D4["DROP"]
    C -- "no" --> OK["Put message into the<br/>target's inbox"]
    D1 --> ST["counters: sent, delivered, dropped"]
    D2 --> ST
    D3 --> ST
    D4 --> ST
    OK --> ST
```

| Message | Sent by | Purpose | Key fields |
|---|---|---|---|
| `RequestVote` | candidate | ask for a vote | term, lastLogIndex, lastLogTerm |
| `VoteReply` | any node | grant or deny | term, granted |
| `AppendEntries` | leader | heartbeat (no entries) or replication | term, prevLogIndex, prevLogTerm, entries, leaderCommit |
| `AppendReply` | follower | accept or reject entries | term, success, matchIndex (or retry hint) |

## Persistence layer

Raft requires `currentTerm`, `votedFor` and the log to survive crashes, otherwise a restarted node could vote twice in one term and break safety. The `Storage` interface has three interchangeable implementations.

```mermaid
flowchart TD
    Start(["Main reads --storage option"]) --> T{"value?"}
    T -- "memory" --> MEM["MemoryStorage<br/>(RAM only)"]
    T -- "file" --> FIL["FileStorage<br/>data/node-N.state, node-N.log"]
    T -- "sqlite or auto" --> Try["Try to load org.sqlite.JDBC<br/>and open data/raft.db"]
    Try --> OK{"driver jar<br/>found in lib/?"}
    OK -- "yes" --> SQL["SqliteStorage<br/>(JDBC)"]
    OK -- "no" --> FB["Log a message and fall back"] --> FIL
    MEM --> R{"--resume given?"}
    FIL --> R
    SQL --> R
    R -- "no (default)" --> Clear["storage.clear()<br/>start with a fresh cluster"]
    R -- "yes" --> Keep["keep old data<br/>nodes resume their terms and logs"]
```

**SQLite schema** (created automatically by `SqliteStorage`):

```mermaid
erDiagram
    NODE_STATE {
        int node_id PK
        int current_term
        int voted_for
    }
    RAFT_LOG {
        int node_id PK
        int log_index PK
        int term
        string command
    }
    NODE_STATE ||--o{ RAFT_LOG : "one node has many entries"
```

CRUD operations used: `CREATE TABLE` + `INSERT` (create), `SELECT` (retrieve), `UPDATE` (term and vote changes), `DELETE` (clear, log rewrite). Log writes run in a **transaction** with `rollback` on failure and a `finally` block that restores auto-commit.

## Threading and synchronization model

```mermaid
flowchart LR
    subgraph T1["Node threads (one per node)"]
        N["RaftNode.run()<br/>all state changes inside<br/>synchronized(this)"]
    end

    subgraph T2["Network delivery threads (2, daemon)"]
        D["ScheduledExecutorService<br/>delivers messages after the delay"]
    end

    subgraph T3["Swing Event Dispatch Thread"]
        U["Buttons, refresh Timer (200 ms),<br/>event log listener"]
    end

    subgraph T4["Main thread / console"]
        M["Cluster calls: submit, crash,<br/>recover, partition"]
    end

    D -- "inbox.offer(message)<br/>(BlockingQueue, thread safe)" --> N
    N -- "network.send()" --> D
    U -- "cluster.snapshots()" --> SNAP["synchronized snapshot()<br/>returns an immutable<br/>NodeSnapshot"]
    M -- "synchronized submit(), crash(), recover()" --> N
    SNAP --- N
```

* **Inbox**: a `LinkedBlockingQueue`, so network threads can hand over messages safely.
* **State**: every method that reads or writes term, vote, log or role is `synchronized`, so the node thread, client calls and UI never see half-updated data.
* **UI isolation**: the GUI and tests never touch live nodes. They read an immutable `NodeSnapshot`.
* **Live events**: `EventLog` is thread-safe; the GUI registers a listener and hops onto the Swing thread with `SwingUtilities.invokeLater`.

## Requirements

* **JDK 17 or newer** (check with `javac -version`). Nothing else is needed to compile and run.
* *Optional:* the `sqlite-jdbc` jar in `lib/` to use the database back-end (see `lib/README.txt`). Without it, the program saves to plain files automatically.
* A graphical desktop for the GUI. On a machine without one the program starts in console mode.

## Quick start

```bash
git clone https://github.com/shivansh07adi-cloud/raft-lite.git
cd raft-lite

./build.sh          # compile into ./out
./run.sh            # open the GUI dashboard
./run.sh --cli      # interactive console
./run.sh --demo     # automatic walk-through of every feature
./run-tests.sh      # automated test suite
```

On Windows use `build.bat`, `run.bat` and `run-tests.bat`. Without scripts:

```bash
mkdir out
javac -d out $(find src test -name "*.java")
java -cp "out:lib/*" raft.Main        # Windows: java -cp "out;lib\*" raft.Main
```

## Command line options

| Option | Meaning |
|---|---|
| `--nodes N` | number of nodes, 1 to 9 (default 5) |
| `--gui` / `--cli` / `--demo` | choose the interface (default: GUI if a screen exists, otherwise CLI) |
| `--storage auto\|sqlite\|file\|memory` | persistence back-end (default `auto`: SQLite if the jar exists, else files) |
| `--data DIR` | folder for saved data (default `data`) |
| `--resume` | keep data from a previous run instead of starting fresh |
| `--fast` | very short timeouts (used by tests and CI) |

## Using the GUI

```mermaid
flowchart TB
    subgraph Window["Dashboard window"]
        direction TB
        subgraph Ctl["Controls"]
            direction LR
            C1["Node selector<br/>Crash / Recover"]
            C2["Crash leader<br/>Recover all"]
            C3["Split... / Heal<br/>(partition)"]
            C4["Command field<br/>Send to leader"]
            C5["Message loss slider"]
        end
        subgraph Mid["Main area"]
            direction LR
            V1["Cluster view<br/>nodes on a circle,<br/>colour = role"]
            subgraph R["Right side"]
                direction TB
                V2["Node status table<br/>role, term, vote, log,<br/>commit, state machine"]
                V3["Replicated logs<br/>committed or pending"]
            end
        end
        V4["Live event log<br/>elections, votes, commits, crashes"]
    end
    Ctl --> Mid --> V4
```

| Colour in the cluster view | Meaning |
|---|---|
| Green | Leader |
| Blue | Follower |
| Orange | Candidate |
| Grey | Crashed |
| Red ring | **Stale leader**: still believes it leads, but a newer term exists |
| Dashed red line | Leader cannot reach this node (crashed or on the other side of a partition) |

**Things to try**

1. Start the GUI and wait 2 to 3 seconds. A leader appears and the others become followers.
2. Type `SET x 5` and press Enter. Watch the commit index rise on every node and `x=5` appear in each state machine.
3. Press **Crash leader**. After an election timeout a new leader is elected with a **higher term**.
4. Press **Recover all**. The old leader returns as a follower and catches up its log.
5. Press **Split...** and enter `1,2|3,4,5`. Send commands, then **Heal** and compare the logs.
6. Move the **message loss** slider to 30% and keep sending commands.

## Console commands

| Command | Action |
|---|---|
| `status` | table of every node: role, term, vote, leader, log size, commit, applied, state machine |
| `logs` | full log of every node, marked committed or pending |
| `send <cmd>` | client command to the leader: `SET x 5`, `x=5`, `DEL x` |
| `crash <id\|leader>` | crash a node or the current leader |
| `recover <id\|all>` | restart crashed nodes |
| `partition 1,2\|3,4,5` | split the network into groups |
| `heal` | repair the network |
| `drop <percent>` | random message loss, for example `drop 20` |
| `delay <min> <max>` | network delay range in milliseconds |
| `trace on\|off` | print every message sent |
| `help`, `quit` | |

## Scripted demo

`./run.sh --demo` runs a complete scenario and checks the result at each step. It is ideal for a presentation.

```mermaid
flowchart TD
    S1["STEP 1<br/>Leader election<br/>wait until a leader exists"] --> S2["STEP 2<br/>Log replication<br/>SET x 10, SET y 20<br/>wait for all nodes to converge"]
    S2 --> S3["STEP 3<br/>Fault tolerance<br/>crash the leader, new leader elected,<br/>SET z 30"]
    S3 --> S4["STEP 4<br/>Recovery<br/>old leader restarts and catches up"]
    S4 --> S5["STEP 5<br/>Network partition<br/>isolate old leader plus one node,<br/>send SET stale 1 to the isolated leader,<br/>majority elects a new leader, SET w 99"]
    S5 --> S6["STEP 6<br/>Heal the partition<br/>stale entry discarded,<br/>all logs identical"]
    S6 --> Done(["All checks PASS<br/>exit code 0"])
```

Sample output (abridged; terms and node numbers vary from run to run):

```text
STEP 3: Fault tolerance - crash the leader, a new one is elected
[18:30:33.050] N3 *** CRASHED *** (was Leader, term 1)
[18:30:33.183] N5 election timeout -> CANDIDATE for term 2
[18:30:33.196] N5 won the election -> LEADER of term 2 (votes: 1,4,5)
   [PASS] new leader elected
   [PASS] new leader is a different node

Node  Role       Term  Voted  Leader  Log   Commit  Applied  State machine
N1    Follower   2     N5     N5      5     5       5        {x=10, y=20, z=30}
N2    Follower   2     N5     N5      5     5       5        {x=10, y=20, z=30}
N3    CRASHED    1     N3     N3      3     3       3        {x=10, y=20}
N4    Follower   2     N5     N5      5     5       5        {x=10, y=20, z=30}
N5    Leader     2     N5     N5      5     5       5        {x=10, y=20, z=30}

STEP 6: Heal the partition - the stale entry is discarded
[18:30:35.135] N5 log conflict at index 6 -> deleting entries from 6 onwards
   [PASS] cluster converged again
   [PASS] stale command never reached the state machine
```

## Testing

`./run-tests.sh` runs eight scenarios with fast timeouts (about 30 seconds in total). GitHub Actions runs the same suite on every push.

```mermaid
flowchart LR
    subgraph Scenarios["8 test scenarios, 34 checks"]
        direction TB
        T1["1 Leader election<br/>exactly one leader"]
        T2["2 Log replication<br/>10 commands reach all nodes"]
        T3["3 Leader crash<br/>re-election and recovery"]
        T4["4 Minority partition<br/>no commit, entry discarded after heal"]
        T5["5 No quorum<br/>3 of 5 down means no leader"]
        T6["6 Message loss<br/>20% random drops"]
        T7["7 Persistence<br/>term and log survive a crash"]
        T8["8 Single node cluster"]
    end

    subgraph Safety["Continuous safety sampler"]
        SS["Background thread samples all nodes every 2 ms<br/>and records the leader of each term"]
        SS --> SV{"two different leaders<br/>in the same term?"}
        SV -- "yes" --> F["test FAILS"]
        SV -- "no" --> P["election safety holds"]
    end

    Scenarios --> Safety
```

Expected last line:

```text
RESULT: 34 passed, 0 failed
```

## Configuration

All timing lives in `core/RaftConfig.java` (milliseconds).

| Parameter | `demo()` (default GUI/CLI) | `fast()` (tests, `--fast`) |
|---|---|---|
| Election timeout (random range) | 1500 to 3000 | 150 to 300 |
| Heartbeat interval | 500 | 50 |
| Thread tick (inbox wait) | 10 | 5 |
| Network delay (random range) | 40 to 150 | 1 to 8 |
| Max entries per `AppendEntries` | 10 | 10 |

The heartbeat is much shorter than the election timeout, so a healthy leader always resets followers' timers in time. The state machine accepts `SET key value`, `key=value` and `DEL key`.

## Project layout

```text
raft-lite/
├── src/raft
│   ├── Main.java                  <- entry point and command line options
│   ├── core/                      <- the Raft algorithm
│   │   ├── RaftNode.java            one server: election, replication, crash/recovery (thread)
│   │   ├── NodeState.java           FOLLOWER / CANDIDATE / LEADER (enum)
│   │   ├── LogEntry.java            generic log entry <T>
│   │   ├── ReplicatedLog.java       generic log <T> (ArrayList, Iterable)
│   │   ├── StateMachine.java        interface
│   │   ├── KeyValueStateMachine.java
│   │   ├── RaftConfig.java          timing parameters
│   │   └── NodeSnapshot.java        immutable read-only view of a node
│   ├── net/                       <- simulated network and messages
│   │   ├── Message.java             abstract base class
│   │   ├── RequestVote / VoteReply / AppendEntries / AppendReply
│   │   ├── MessageHandler.java      interface implemented by RaftNode
│   │   ├── MessageType.java
│   │   └── Network.java             delay, loss, partitions
│   ├── storage/                   <- persistence
│   │   ├── Storage.java             interface
│   │   ├── SqliteStorage.java       JDBC + SQLite
│   │   ├── FileStorage.java         java.io files
│   │   ├── MemoryStorage.java
│   │   ├── PersistentState.java
│   │   └── StorageFactory.java      picks the back-end
│   ├── sim/
│   │   ├── Cluster.java             creates nodes, failure injection, client interface
│   │   └── DemoScenario.java        scripted demo
│   ├── ui/                        <- ConsoleUI, SwingDashboard, ClusterPanel, StatusFormatter
│   ├── exception/                 <- NodeCrashed, NotLeader, NetworkPartition, Storage exceptions
│   └── util/                      <- EventLog, Util (generic methods)
├── test/raft/RaftTests.java       <- automated tests
├── docs/
│   ├── CODE_WALKTHROUGH.md          reading order, flows, viva questions
│   ├── REPORT_OUTLINE.md            chapter outline for the project report
│   └── images/                      screenshots used in this README
├── lib/                           <- optional sqlite-jdbc jar goes here
├── data/                          <- saved state at runtime (git-ignored)
├── .github/workflows/ci.yml       <- GitHub Actions CI
├── build.sh / build.bat
├── run.sh / run.bat
└── run-tests.sh / run-tests.bat
```

## Syllabus mapping

How the project covers every unit of the Java course:

```mermaid
flowchart LR
    P(("Raft-lite"))

    P --> U1["Unit 1<br/>Classes, inheritance,<br/>abstract class, interface, packages"]
    P --> U2["Unit 2<br/>Polymorphism and<br/>exception handling"]
    P --> U3["Unit 3<br/>Multithreading, I/O,<br/>event handling"]
    P --> U4["Unit 4<br/>JDBC with SQLite<br/>and Collections"]
    P --> U5["Unit 5<br/>Generics and<br/>GUI programming"]

    U1 --> U1a["abstract Message + 4 subclasses<br/>interfaces StateMachine, Storage, MessageHandler<br/>8 packages, static final NOOP"]
    U2 --> U2a["dispatch() and describe() overridden<br/>3 Storage implementations<br/>4 user-defined exceptions, try / catch / finally"]
    U3 --> U3a["RaftNode implements Runnable<br/>synchronized methods, BlockingQueue<br/>FileStorage readers and writers<br/>ActionListener, WindowAdapter"]
    U4 --> U4a["SqliteStorage: create, retrieve,<br/>update, delete, transactions<br/>ArrayList, HashSet, HashMap, TreeMap, Iterator"]
    U5 --> U5a["LogEntry T, ReplicatedLog T<br/>Util.join Collection of wildcard, Util.firstOr<br/>Swing / AWT dashboard"]
```

| Unit | Topic | Where it is used |
|---|---|---|
| 1 | Classes, objects, constructors, `static`, `final` | every class; `RaftNode.NOOP` (static final), `RaftConfig` |
| 1 | Inheritance, abstract class, interface, packages | abstract `Message` and 4 subclasses; interfaces `StateMachine`, `Storage`, `MessageHandler`; 8 packages |
| 2 | Compile-time and runtime polymorphism | overloaded constructors; `dispatch()` and `describe()` overridden per message; `Storage` has 3 implementations |
| 2 | Exceptions: try / catch, multiple catch, throw, throws, finally, user-defined | `NodeCrashedException`, `NotLeaderException`, `NetworkPartitionException`, `StorageException`; rollback + `finally` in `SqliteStorage.saveLog` |
| 3 | Threads, `Runnable`, synchronization, inter-thread communication | each `RaftNode` is a thread; `synchronized` methods; `BlockingQueue` inbox |
| 3 | I/O streams | `FileStorage` (`FileReader`, `FileWriter`, `BufferedReader`, `PrintWriter`) |
| 3 | Event handling, delegation model, adapter classes | `SwingDashboard`: `ActionListener`, `WindowAdapter` |
| 4 | JDBC with SQLite: create database and tables, create / update / delete / retrieve | `SqliteStorage` |
| 4 | Collections: List, Set, Map, `ArrayList`, `HashSet`, `HashMap`, `TreeMap`, `Iterator` | log (`ArrayList`), votes (`HashSet`), `nextIndex` / `matchIndex` (`HashMap`), `Iterable` log |
| 5 | Generic classes, generic methods, wildcards | `LogEntry<T>`, `ReplicatedLog<T>`, `Util.join(Collection<?>)`, `Util.firstOr` |
| 5 | GUI programming | Swing / AWT dashboard |

> **Note on the GUI toolkit:** the syllabus lists JavaFX, but JavaFX is not part of the JDK and needs a separate SDK. To keep the project runnable with only a JDK, the dashboard uses **Swing / AWT**, which ships with Java and uses the same event model taught in Unit 3.

## Design decisions and limitations

* **Single JVM, simulated network.** Nodes are threads and messages travel through a scheduler instead of sockets. This keeps the project runnable anywhere and makes fault injection exact and repeatable.
* **No-op entry on election.** A new leader appends `NOOP`, so entries from earlier terms can be committed safely (a leader only commits entries of its own term directly).
* **Randomized timeouts** avoid split votes; heartbeats are much faster than timeouts.
* **Whole-log persistence.** The log is saved as a whole each time it changes. This is simple and correct for a demo but would be replaced by append-only writes in a real system.
* **Not implemented** (outside "Raft-lite"): cluster membership changes, log compaction and snapshots, pre-vote and leader leases. After a partition heals, a briefly isolated node can trigger an extra election because it increased its term while alone. This is normal in Raft without pre-vote.

## Future work

```mermaid
flowchart LR
    Now["Current version<br/>election, replication,<br/>faults, persistence,<br/>GUI, tests"] --> A["Pre-vote<br/>no disruption after<br/>a partition heals"]
    Now --> B["Log compaction<br/>and snapshots"]
    Now --> C["Membership changes<br/>add and remove nodes"]
    Now --> D["Real sockets<br/>nodes in separate processes"]
    Now --> E["JavaFX dashboard<br/>with message animations"]
    Now --> F["Append-only storage<br/>instead of whole-log rewrite"]
```

## Upload to GitHub

```bash
cd raft-lite
git init
git add .
git commit -m "Distributed Consensus Simulator (Raft-lite)"
git branch -M main
git remote add origin https://github.com/shivansh07adi-cloud/raft-lite.git
git push -u origin main
```

Or create an empty repository on github.com and use **Add file > Upload files** to drag in the unzipped folder contents.

## Author

**Shivansh Kumar** | AP25110010113 | Section E, 2nd Year, 3rd Semester | SRM University AP
Portfolio: [shivanshonline.in](https://shivanshonline.in)
