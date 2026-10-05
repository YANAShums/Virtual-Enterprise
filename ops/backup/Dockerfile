FROM mysql:8.4

RUN microdnf install -y gzip tar findutils && microdnf clean all
WORKDIR /opt/flowstaff
COPY ops/backup/backup.sh ops/backup/restore.sh ./
RUN chmod 0555 backup.sh restore.sh

ENTRYPOINT ["/opt/flowstaff/backup.sh"]
