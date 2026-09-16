FROM liquibase/liquibase:4.31.1
# Install once at build time: INSTALL_MYSQL in the entrypoint fails on restart.
RUN lpm add mysql@8.0.30 --global
