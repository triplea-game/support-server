Powers the "map tags" admin page where MapAdmins maintain one flat, ordered list
of free map tags (e.g. "naval"). Assigning tags to maps comes later.

The page uses HTMX the same way as the map attributes page (see
`../attributes/README.md`): each mutation posts in the background and swaps back
the re-rendered tag row (rename) or the whole list (add, delete, move). Every
control keeps its plain `action`/`formaction`, so it still works with JavaScript
disabled (POST-redirect-GET when the `HX-Request` header is absent).
