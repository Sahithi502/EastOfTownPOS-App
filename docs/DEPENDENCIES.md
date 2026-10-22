# Dependencies and notices

Installed versions are locked in package-lock.json and the Maven dependency graph. Recheck security advisories and supported release lines before a production release.

| Component | Version | License / purpose |
|---|---|---|
| Spring Boot | 3.5.16 | Apache-2.0; REST, validation, JDBC, transaction management |
| SQLite JDBC | 3.53.4.0 | Apache-2.0 / BSD-2-Clause driver; SQLite engine public domain |
| React / React DOM | 19.2.0 | MIT; renderer |
| TypeScript | 5.9 compatible | Apache-2.0; static checking |
| Vite | 7.3.7 locked | MIT; renderer build |
| Electron | 44.9.3 locked | MIT plus Chromium/Node third-party notices |
| Electron Builder | 26.15.3 pinned | MIT; NSIS packaging (NSIS has separate bundled notices) |
| Temurin JRE | 21.0.12.1+1 | GPLv2 with Classpath Exception and third-party notices; legal directory and source/checksum manifest included |
| JUnit / Spring Boot Test | Maven managed | EPL-2.0 / Apache-2.0; testing |

Official compatibility references: [Spring Boot 3.5 requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html), [Electron releases](https://releases.electronjs.org/), [SQLite JDBC](https://github.com/xerial/sqlite-jdbc).

Audit on 22 October 2026: npm reports eight moderate advisories in the development packaging dependency chain (`sprintf-js` through `roarr`/`global-agent`/`@electron/get` into Electron Builder). No high/critical advisories were reported in this pinned npm graph. Generic `npm audit fix` selected an older builder with worse advisories, so builder 26.15.3 was restored. Resolve/review the remaining development advisories before production distribution. This is not a comprehensive Java/Chromium/runtime security certification.

Planned, not yet installed: Apache POI (Apache-2.0) for spreadsheets, ZXing core (Apache-2.0) for Code 128 and Apache PDFBox (Apache-2.0) for PDFs. Use Electron/Windows printer APIs with explicit job error handling and 58/80 mm templates; barcode labels require measured printer calibration. Pin supported versions at the stage where each dependency is introduced. Retain upstream copyright/license notices and review transitive dependencies and the Java runtime redistribution terms when packaging. Do not substitute AGPL/commercial PDF libraries without review.
