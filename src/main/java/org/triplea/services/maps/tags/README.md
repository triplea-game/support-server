Powers the "map tags" admin page where MapAdmins maintain one flat, ordered list
of free map tags (e.g. "naval"). MapAdmins assign them to maps on the status page
(`../status`); the map listing (`../listing`) sends each as a `MapTag` named "Tags".

Tags replace the earlier map attributes (one value per dimension, e.g.
"difficulty: easy"). The migration `V2.00.09__map_attributes_to_tags.sql` turned
every attribute value into a tag and every map's values into its tags. The
attribute tables stay until a later release drops them, so the previous release
still works after a rollback.

The page is enhanced with HTMX: each mutation posts in the background and swaps
back the re-rendered tag row (rename) or the whole list (add, delete, move)
instead of reloading the page. Every control keeps its plain
`action`/`formaction`, so it still works with JavaScript disabled
(POST-redirect-GET when the `HX-Request` header is absent).

HTMX is vendored, not loaded from a CDN, so the app stays self-contained behind the
reverse proxy: `src/main/resources/META-INF/resources/support/htmx.min.js`
(htmx 2.0.4, BSD-2-Clause), served at `/support/htmx.min.js`. Upgrading means
replacing that file.
