# Hosting the initializer later

This release is ready for local use and includes the controls needed for a hosted trial. It has not been deployed or load-tested in a production environment.

## Runtime

Use a VM/service that runs Java 21. Copy the JAR and start it with:

```sh
PORT=9090 BIND_ADDRESS=127.0.0.1 java -jar technotes-initializr-v4.jar
```

Keep loopback binding when a reverse proxy runs on the same host. Set `BIND_ADDRESS=0.0.0.0` only when your hosting platform requires it; restrict direct access at the firewall/platform.
Use a service manager to restart the process. GET `/health` returns UP.

## Reverse proxy requirements

- Serve HTTPS; redirect HTTP to HTTPS.
- Preserve the browser's external Host header. POST Origin is compared to Host. No wildcard CORS.
- Allow only your actual hostname at the proxy.
- Limit request body to 32 KB, header size, request rate, open connections and request duration. The app has a bounded worker pool/body size, but public traffic still needs edge limits.
- Serve only the JAR application routes, not the source/workspace folder.
- Add private team authentication at the proxy if it should be a team-only tool.
- Run Java as an unprivileged account; do not put database passwords or owner keys on this server.

The hosted generator is separate from the generated OAuth service. Hosting this generator does not change OAuth issuer, React origin, callback or Gateway contract.
Saved catalogs live in each user's browser, so there are no shared server catalog-edit routes or user credentials in the generator.
The generator does not contact Maven or execute submitted dependencies; it only creates text ZIP entries in memory.

Before public release, test through the actual proxy: HTTPS, allowed host/origin, oversized requests, rate limits, ZIP download, and browser console. Review Java/runtime security updates regularly.

## Browser-only GitHub Pages version (4.1)

The repository also builds a static version with `sh build-site.sh` (JDK 21 at build time only).
`src/ExportBrowser.java` exports the catalog and dependency templates from the Java generator.
`browser/engine.js` validates inputs and generates source/configuration locally; `browser/zip.js` writes the ZIP in memory.
No Java runtime or server is needed by the hosted browser version. Form values are not sent to a backend.

One-time repository setup: Settings > Pages > Build and deployment > Source > GitHub Actions.
Then `.github/workflows/pages.yml` builds and deploys on every push to main, or manually through Run workflow.
Only changes merged/pushed to main deploy; local edits and unmerged feature branches do not.
Failed builds leave the previous deployment in place. Check the Actions run before expecting an update.
Already-open pages check deployment revision every 60 seconds and offer a reload button when changed.
Refreshing loads the new deployed version; open forms are not auto-reloaded.
Expected URL after the FIRST successful deployment: https://techshakti55.github.io/technotes-initializr/
This URL must not be described as live until deployment succeeds.
