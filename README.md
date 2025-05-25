TODO:
- Fare chiamata a GET /maps/{guid} e vedere se c'e' anche li base64 della full map, oppure curl http://192.168.12.20/?mode=get-map-data&id=73da3a2a-2c14-11f0-bd7c-00012978ebab&pageviewid=682f3ee89f979
- Testare la protective scan: curl 'http://192.168.12.20/?mode=get-tmp-png&calltype=light&d=1747912980292&f=web_images/protective_scan.png' \
  -H 'Accept: image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8' \
  -H 'Accept-Language: en-US,en;q=0.5' \
  -H 'Connection: keep-alive' \
  -b 'mir-auth-token=; mir_login_type=regular; mir_user_id=9; mir_user_shortcode=7428944180; menu_desktop_visible=true; ' \
  -H 'Referer: http://192.168.12.20/monitoring/safety-system' \
  -H 'Sec-GPC: 1' \
  -H 'User-Agent: Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36' \
  --insecure
  + {"op":"call_service","id":"call_service:/mirwebapp/getProtectiveScan:113","service":"/mirwebapp/getProtectiveScan","args":{}}
- 