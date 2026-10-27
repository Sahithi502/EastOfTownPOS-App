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
| Apache POI / OOXML | 6.0.0 | Apache-2.0; Excel template, parser and validation export |
| ZXing core | 3.5.4 | Apache-2.0; Code 128 encoding/decoding tests |
| Apache PDFBox | 3.0.8 | Apache-2.0; label and receipt PDF generation/rendering and Java print integration |
| Spring Security Crypto | 6.5.11 (Boot managed) | Apache-2.0; BCrypt password hashing |
| Playwright Test | 1.64.1 | Apache-2.0; isolated Electron end-to-end tests, development only |

Official compatibility references: [Spring Boot 3.5 requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html), [Electron releases](https://releases.electronjs.org/), [SQLite JDBC](https://github.com/xerial/sqlite-jdbc).

Audit on 22 October 2026: npm reports eight moderate advisories in the development packaging dependency chain (`sprintf-js` through `roarr`/`global-agent`/`@electron/get` into Electron Builder). No high/critical advisories were reported in this pinned npm graph. Generic `npm audit fix` selected an older builder with worse advisories, so builder 26.15.3 was restored. Resolve/review the remaining development advisories before production distribution. This is not a comprehensive Java/Chromium/runtime security certification.

Stage 2 audit on 24 October 2026: `npm install --package-lock-only --ignore-scripts` reported zero vulnerabilities in the current registry audit. The historical Stage 1 result above is retained as earlier evidence. Java dependencies and the bundled runtime still require release security review. Retain upstream notices and licenses when distributing installers. Spring JCL supplies Commons Logging; the duplicate commons-logging dependency from PDFBox is excluded.

POI 6.0.0 requires Java 17+ and is compatible with the project's Java 21 bytecode target. Official sources: [POI releases](https://poi.apache.org/download.cgi), [ZXing releases](https://github.com/zxing/zxing/releases), [PDFBox](https://pdfbox.apache.org/). System fonts used for PDF text are loaded from the OS and are not redistributed in this repository; review embedding rights for fonts installed by the operator. Stage 3 reuses PDFBox and Java printing for 58/80 mm receipts; Stage 2 supports labels with driver calibration and PDF fallback. Stage 4 reuses POI/PDFBox for report exports with no new dependencies or version changes. No paid cloud or signing service was added.

Stage 5 (0.5.0) adds no third-party dependencies. Backup operations use the existing Xerial SQLite JDBC driver and Java SQL/filesystem APIs; PNG uses existing PDFBox rendering and Java ImageIO. WhatsApp uses the documented click-to-chat link through Electron shell.openExternal; no WhatsApp SDK, paid service, credential storage or automated messaging is included. Existing dependency license notices remain applicable.

Stage 6 (0.6.0) adds `com.googlecode.libphonenumber:libphonenumber:9.0.41`, Apache-2.0, for offline country/mobile validation and E.164 normalization. Preserve its license/notices in distribution and update phone metadata through reviewed releases. Official source: https://github.com/google/libphonenumber/tree/v9.0.41. Metadata validation does not prove ownership or WhatsApp registration.

The original ReceiptShare C# helper uses the Windows .NET Framework 4.8 compiler/runtime, WinForms and OS Windows Runtime metadata. These are Windows platform prerequisites and are not redistributed from Windows directories. No NuGet package, Windows SDK runtime, WhatsApp SDK or paid service is added. The native executable is a release artifact, rebuilt from source and included beside the Java runtime. See RELEASE.md for separate helper/application/installer signing. Existing PDFBox and fonts generate the same 80 mm PNG/PDF layout; existing barcode behavior is unchanged.

Stage 6 audit on 26 October 2026: `npm.cmd audit --json` reported zero vulnerabilities across the locked graph (354 total dependencies including development/optional entries). Java dependencies, OS/runtime updates and physical release acceptance remain separate review responsibilities. The new library's Apache-2.0 license is also included under docs/licenses/.
