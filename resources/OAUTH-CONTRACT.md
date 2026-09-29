# TechNotes fixed first-live OAuth contract

This generator creates a scaffold only. The following remains to be implemented and integration-tested.

| Method | Path | Contract |
|---|---|---|
| GET | /oauth2/authorize | Framework Authorization Code + PKCE S256; client_id=technotes-web; exact redirect_uri=http://localhost:5173/auth/callback; response_type=code; scope, state, nonce, code_challenge, code_challenge_method=S256. Return code and the same state. |
| GET | /login | Issuer password form; never collect owner password in React. |
| POST | /login | Framework form submission with session and CSRF; resume authorization. Not a JSON API. |
| POST | /oauth2/token | Form-encoded authorization_code grant, public client_id, code, exact redirect_uri and code_verifier. No client secret in React. Return access_token, token_type=Bearer, expires_in. Allow exact UI origin. |
| GET | /oauth2/jwks | Public signing JWK Set only; never private keys. |
| GET | /api/v1/users/me | Gateway-visible business API, bearer + profile.read; direct JSON {id,displayName,email,roles,status}; 401 missing/invalid token, 403 missing scope. |

Issuer: http://localhost:9000. UI: http://localhost:5173. Gateway: http://localhost:8080.
Privately provision Shakti's ACTIVE ADMIN account in PostgreSQL (AUTHOR if Notes requires it).
Scopes: openid profile notes.read notes.write notes.review taxonomy.write profile.read.
Access token: exact iss; immutable UUID sub; aud=[technotes-api]; space-separated scope; roles array; iat, nbf, exp.
Signing keys and OAuth authorization state must survive restart. Never generate a new signing key on every startup.

No public signup, user-management API, password reset, email verification, refresh-token flow, introspection/revocation or Premium roles in this release. Framework OIDC/discovery metadata may be exposed but is not called by the UI. Use framework protocol endpoints; do not build replacements.

Acceptance: callback/code exchange succeeds once; wrong verifier and code replay fail; /me returns owner identity; Notes accepts access tokens but rejects wrong issuer/audience/signature/expiry and ID tokens. Exact CORS/callback origins. Review HTTPS origins and callback before AWS deployment.
