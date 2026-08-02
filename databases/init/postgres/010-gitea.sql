SELECT 'CREATE DATABASE gitea OWNER platform'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'gitea')\gexec
