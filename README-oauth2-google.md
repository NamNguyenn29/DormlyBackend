# Google OAuth2 flow (DormlyBackend)

## Overview of how this backend works

The backend implements Google OAuth2 login via Spring Security and then exchanges a short-lived auth code for your JWT.

### 1) Start OAuth2 login
- Open:
  - `GET {backend}/oauth2/authorization/google`

Spring Security redirects to Google.

### 2) On successful Google login
`OAuth2SuccessHandler` runs:
- Creates a short-lived one-time code using `OAuth2AuthCodeStore.generate(email)`
- Stores the one-time code in an **HttpOnly cookie** named:
  - `OAUTH2_CODE`
- Cookie settings:
  - `HttpOnly=true`
  - `Path=/api/auth/oauth2/token`
  - `Max-Age=300` (5 minutes)
- Then backend redirects to:
  - `${app.frontend-url}/oauth2/callback`

### 3) Exchange cookie for JWT
Frontend calls:
- `POST {backend}/api/v1/auth/oauth2/token`
- No body required
- Backend reads cookie value `OAUTH2_CODE` using `@CookieValue`
- Backend calls `OAuth2AuthCodeStore.consumeEmail(code)`:
  - returns email if valid & not expired, otherwise `null`
- If valid, backend loads/creates the user and issues JWT:
  - `accessToken` (and refresh cookie for refresh flow)

## Common errors to check

1) **Missing/expired code**
- HTTP 400/401 from `exchangeOAuth2Code`:
  - `Missing OAuth2 code`
  - `Invalid or expired OAuth2 code`
- Likely causes:
  - Cookie not present (HttpOnly cookie; browser devtools won’t show via `document.cookie`)
  - Cookie path mismatch
  - Callback page did not call the exchange endpoint with `credentials: 'include'`

2) **User not found / role not found**
- When Google user is new, `CustomOAuth2UserService` creates it.
- It requires a role named `USER` (or it will throw `Role USER not found in DB`).

3) **OAuth2 login failed**
- Redirect to:
  - `${frontend}/login?error=oauth2_failed`

## Test HTML
- `oauth2-google-test.html`
  - Button 1: starts login
  - Button 2: calls token exchange endpoint

