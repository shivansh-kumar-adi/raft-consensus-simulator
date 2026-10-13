# Project Report Outline (SRM format, adapt headings to the official template)

1. Title page (name, registration no., section, semester, guide, department, academic year)
2. Certificate / Declaration / Acknowledgement
3. Abstract (150 to 200 words)
4. Table of contents, list of figures, list of tables
5. **Chapter 1 Introduction**: problem, motivation (etcd, Kubernetes, databases), objectives, scope
6. **Chapter 2 Literature / Background**: distributed systems, consensus, Paxos vs Raft, CAP theorem
7. **Chapter 3 System design**: architecture diagram, package/class diagram, state diagram (follower/candidate/leader),
   sequence diagrams (election, log replication), database schema
8. **Chapter 4 Implementation**: modules (core, net, storage, sim, ui), threading model, synchronization,
   exception handling, JDBC layer, GUI; syllabus mapping table (from README)
9. **Chapter 5 Testing and results**: test cases table (8 scenarios), screenshots of the GUI, demo output
10. **Chapter 6 Conclusion and future work**: membership changes, snapshots, real sockets, JavaFX UI, pre-vote
11. References (Ongaro and Ousterhout, "In Search of an Understandable Consensus Algorithm"; raft.github.io; Java docs)
12. Appendix: key source listings, GitHub link, how to run

Screenshots to take: dashboard with a leader, after "Crash leader", during a partition (red ring), the demo output.
