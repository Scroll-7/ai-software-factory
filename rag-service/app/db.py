from contextlib import contextmanager

from .config import settings


@contextmanager
def connection():
    """Import paresseux : /health fonctionne même si psycopg n'est pas encore installé."""
    import psycopg
    with psycopg.connect(settings.database_url) as conn:
        yield conn
