openssl s_client -connect prod.triplea-game.org:443 2>/dev/null < /dev/null \
    | sed -n '/BEGIN CERTIFICATE/,/END CERTIFICATE/p' > prod.triplea-game.org

