# Browser regression tests

The Playwright suite has four deliberately separate layers:

- `npm run test:e2e` runs functional and runtime-health checks at desktop, narrow desktop, and Pixel 7 sizes.
- `npm run test:e2e:motion` runs the FPS, long-task, layout-shift, transient-opacity, and modal-close diagnostics in a visible Chromium window. Headless Chromium throttles its compositor and is not a trustworthy FPS source. Motion runs always retain a trace and video for frame-by-frame inspection.
- `npm run test:e2e:visual` compares stable public pages with the committed screenshots for the current operating system.
- `npm run test:e2e:smoke` runs the short, read-only public-edge suite used after a production rollout.

The runtime audit fails on browser exceptions, unexpected failed requests or 5xx responses, broken images, horizontal overflow, and semantic controls or headings that remain transparent. Motion diagnostics capture frame intervals, long tasks, and layout shifts. Failure artifacts include screenshots, video, traces, DOM health, runtime errors, and motion metrics under `test-results/artifacts`; open the HTML report with `npm run test:e2e:report`.

## Local prerequisites

The Playwright configuration starts Vite automatically. The API, questions, and budget services still need to be available on their normal local ports when a scenario uses backend data.
Budget gameplay scenarios are reported as skipped when no generated daily challenge exists; they are never counted as a successful gameplay check without exercising the game.

Install Chromium once:

```powershell
npx playwright install chromium
```

Update intentional visual changes and then immediately verify their stability:

```powershell
npm run test:e2e:visual -- --update-snapshots
npm run test:e2e:visual
```

## Signed-in and administrator pages

Credentials are never stored in the repository. Create a local browser state after signing in, save it below the ignored `.auth` directory, and pass its path to Playwright:

```powershell
npx playwright codegen http://localhost:3001/login --save-storage=.auth/admin.json
$env:PLAYWRIGHT_ADMIN_STORAGE_STATE = '.auth/admin.json'
npx playwright test e2e/authenticated.spec.ts --project=chromium-desktop --workers=1
```

Use `PLAYWRIGHT_USER_STORAGE_STATE` for an ordinary account. An administrator state also covers the ordinary signed-in checks. These states contain live session credentials: keep them local, expire them after use, and never commit or upload them as artifacts.

With `PLAYWRIGHT_ADMIN_STORAGE_STATE` set, `npm run test:e2e:motion` also measures real administrator modals: user-editor close latency plus AI-run-detail and statistic-editor opening frames. These runs retain frame-by-frame video and traces.

The same command always runs deterministic administrator modal diagnostics with mocked API data. They render the real AI-run and statistics pages with deliberately dense fixtures, sample every animation frame, and fail if the panel, backdrop, or underlying application flashes transparent while a modal opens.

## Public-edge smoke

To run the exact post-deploy browser checks manually:

```powershell
$env:PLAYWRIGHT_BASE_URL = 'https://clueterra.com'
$env:PLAYWRIGHT_SMOKE_ID = "manual-$([guid]::NewGuid())"
npm run test:e2e:smoke
```

The production smoke is intentionally read-only. Full guest gameplay and administrator audits remain manual/local because they mutate daily attempts or require privileged credentials.
