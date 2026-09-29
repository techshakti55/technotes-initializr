# Browser deployment (4.1)

Browser-only generation and automatic GitHub Pages deployment are now included.
After Pages is enabled with Source = GitHub Actions, every push to main triggers build/test/deploy.
Expected URL after successful deployment: https://techshakti55.github.io/technotes-initializr/
An open tab offers a reload button when a newer deployment is detected (checks every 60 seconds).
See HOSTING.md for one-time setup and build details. Local Java usage remains available below.

# TechNotes Initializr 4.0.0

A local Java project generator enhanced from your Local Initializr V3 source.
Requires **JDK 21**. No Docker, npm, external web assets or Maven installation is required to run the generator.

## Windows quick start

1. Extract this entire ZIP to `D:\TechNotes\tools\technotes-initializr`.
2. Double-click `start.cmd`, or open PowerShell in the extracted folder and run:

```powershell
java -jar technotes-initializr-v4.jar
```

3. Open **http://localhost:9090**.
4. Choose **User / OAuth** (already selected), then review or preview the POM.
5. Generate the project ZIP. Extract to a **new** folder, such as `D:\TechNotes\technotes-user-oauth-service`.
6. Open its `pom.xml` in IntelliJ, choose JDK 21, and reload Maven.
7. Follow the generated README for local database configuration. The generator does not install PostgreSQL or generate working OAuth business logic.

To use a different generator port: `java -jar technotes-initializr-v4.jar 9095`.
The generator port (9090) is separate from the generated OAuth service port (9000).

## Presets

| Preset | Artifact | Package | Port | Default database |
|---|---|---|---|---|
| User / OAuth | technotes-user-oauth-service | com.technotes.auth | 9000 | PostgreSQL |
| Notes | technotes-notes-service | com.technotes.notes | 8081 | MongoDB |
| Eureka | technotes-eureka-server | com.technotes.eureka | 8761 | None |
| Config | technotes-config-server | com.technotes.config | 8888 | Native local folder |
| Gateway | technotes-api-gateway | com.technotes.gateway | 8080 | None |
| Custom | technotes-custom-service | com.technotes.custom | 8082 | User selection |

All presets use group `com.technotes`, Java 21, Spring Boot 3.5.16. Cloud 2025.0.3 is imported only when needed. Boot Admin 3.5.10 is retained for optional catalog selections.
Eureka and Notes POM versions and package declarations were verified on GitHub; Config/Gateway package paths were checked. Ports for Config/Notes are generator defaults, not a claim about their current deployed configuration.

OAuth and Notes presets are standalone first: Eureka and Config clients are optional selections. The OAuth contract remains fixed and is included in the generated project. No public signup or custom OAuth protocol endpoint is generated.

## What changed from V3

- Central version baseline in `resources/versions.properties`.
- Six preset choices including Custom, Java 21 default, correct TechNotes identities.
- Authorization Server, Flyway and Spring Security test dependencies.
- Flyway PostgreSQL/MySQL module automatically follows selected database driver.
- Required dependencies locked in UI and validated on the server.
- POM preview and readable errors without leaving the form.
- Maven Wrapper copied from your Notes repository (Apache Maven Wrapper headers retained).
- Dependency conflict, Java/package/port/name, duplicate and request-size validation.
- Responsive UI, dependency search, explicit scaffold status.
- Environment variable placeholders, schema validation and restricted Actuator exposure.
- Browser-local saved dependency catalog; JSON export/import and legacy V3 TSV import.
- Stateless generation: the server never writes user projects/catalogs to disk.
- Exact route matching, external JS/CSS, CSP, no CORS, and same-origin browser POST checks.
- Source build scripts and a Python HTTP regression suite.

## Saved dependencies: migrating from V3

Your old `local-initializr-custom-dependencies.tsv` can be imported from the Custom dependencies section.
Catalogs are now stored **per browser and origin** (including port), not shared globally on the server.
Export JSON for backup or to move to another browser. Clearing browser data removes the saved catalog.
No uploaded dependency is downloaded or executed by this generator. Maven resolves it later when you build the generated project.
Versions for custom dependencies outside the Spring BOM must be supplied explicitly.

## Build the generator from source

With JDK 21 on PATH:

- Windows: `build.cmd`
- Linux/macOS: `sh build.sh`

The executable JAR includes `resources/`. Editing HTML/CSS/JS or versions requires rebuilding the JAR.
Do not edit Boot, Cloud or Admin versions independently without checking compatibility.
Existing service repositories are **not** changed by updating this generator.

## Verification

Start the JAR, then run `python tests/test_generator.py` (Python 3).
See `TEST-RESULTS.md` for checks actually performed on this release and limits.
Generated scaffolds intentionally contain no empty passing business tests. Add tests as you implement the service. A Maven package alone is not API verification.

## Live hosting

Possible on a Java 21 server behind an HTTPS reverse proxy. See `HOSTING.md`.
Nothing is deployed automatically by this bundle.
