#!/bin/sh
# Drops only the demo schema. Gatekeeper's own database is left alone.
# After this, stop the demo app and delete the demo-shop folder.
# Also delete the demo-shop client in the Gatekeeper console.
set -e
mysql -h 127.0.0.1 -P 3307 -uroot -proot -e "DROP DATABASE IF EXISTS demo_shop;"
echo "Dropped MySQL schema demo_shop."
echo "Stop the app on port 9000, delete the demo-shop folder, and delete the demo-shop client in the console."
