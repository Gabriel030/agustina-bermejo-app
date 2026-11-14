FROM alpine:3.20

RUN apk add --no-cache curl bash

COPY check.sh /usr/local/bin/uptime-check
RUN chmod +x /usr/local/bin/uptime-check

ENTRYPOINT ["/usr/local/bin/uptime-check"]
