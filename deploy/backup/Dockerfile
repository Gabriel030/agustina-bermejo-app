FROM alpine:3.20

RUN apk add --no-cache postgresql16-client age rclone curl tzdata bash

COPY scripts/ /opt/backup/
RUN chmod +x /opt/backup/*.sh

ENV PATH="/opt/backup:${PATH}"
ENTRYPOINT ["/opt/backup/entrypoint.sh"]
