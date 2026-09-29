# Verification — TechNotes Initializr 4.0.0

Verified 29 September 2026.

## Passed

- Generator compiled with OpenJDK 21.0.12 using `javac --release 21`; executable JAR started successfully.
- 47 HTTP regression checks: six preset ZIPs/POMs, preview equivalence, required dependencies, Java/name/package/port validation, conflicting dependencies, duplicate coordinates, request size, origins, methods, static assets and response headers.
- XML parsed for every generated preset, no duplicate built-in Maven coordinates.
- All five service YAML files parsed successfully.
- JavaScript syntax checked with Node.
- UI logic exercised in JSDOM against the running server: default OAuth preset, Java 21, locked required dependencies, POM preview, switching Gateway package, invalid MVC/Gateway selection, browser catalog persistence, dependency search.
- Generated OAuth, Notes, Eureka, Config Server and Gateway projects: Maven `clean package` succeeded with the fixed Boot 3.5.16 baseline. OAuth contains managed Authorization Server, Resource Server, PostgreSQL and Flyway modules.

## Limits

- The generated service scaffolds contain no business tests. Maven package proves dependency resolution/compilation/packaging, not working APIs or authentication.
- PostgreSQL/MongoDB connections, PKCE, token validation and full service integration are future implementation work.
- A real headless Chrome visual test was attempted but this execution environment denied its process socket creation. JSDOM checks are functional DOM checks, not visual desktop/mobile browser verification.
- The generator has not been publicly deployed or load-tested. Follow HOSTING.md and verify the real proxy/domain before release.
- Windows start/build scripts were inspected; execution tests ran on Linux/JDK 21.
- Maven access needed this environment's network proxy. No environment-specific proxy or credential settings are included in the deliverable.

To repeat the included HTTP checks, start the JAR and run `python tests/test_generator.py`.
