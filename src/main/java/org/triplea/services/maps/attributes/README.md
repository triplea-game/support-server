Powers the "map attributes" admin CRUD page for managing the map-attributes
themselves. Once the attributes are setup, they can later be attached
to specific maps.

The page is enhanced with HTMX: each mutation posts in the background and swaps
back the smallest correct re-rendered fragment (a value row, an attribute section,
or the whole list) instead of reloading the page. Every control keeps its plain
`action`/`formaction` so it still works with JavaScript disabled (the endpoints
fall back to POST-redirect-GET when the `HX-Request` header is absent). See
`docs/htmx.md` for the design.

HTMX is vendored, not loaded from a CDN, so the app stays self-contained behind the
reverse proxy: `src/main/resources/META-INF/resources/support/htmx.min.js`
(htmx 2.0.4, BSD-2-Clause), served at `/support/htmx.min.js`. Upgrading means
replacing that file.
