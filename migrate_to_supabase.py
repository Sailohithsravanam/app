import os
import sqlite3
import psycopg2

DB_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "finoraax.db")
ENV_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".env")

def load_api_key(name):
    key = os.environ.get(name, "")
    if not key and os.path.exists(ENV_FILE):
        with open(ENV_FILE, "r") as f:
            for line in f:
                if line.strip().startswith(f"{name}="):
                    key = line.strip().split("=", 1)[1].strip()
                    break
    return key

DATABASE_URL = os.environ.get("DATABASE_URL") or os.environ.get("SUPABASE_DB_URL") or load_api_key("DATABASE_URL") or load_api_key("SUPABASE_DB_URL")
if DATABASE_URL and DATABASE_URL.startswith("postgres://"):
    DATABASE_URL = DATABASE_URL.replace("postgres://", "postgresql://", 1)

def migrate():
    if not DATABASE_URL or not DATABASE_URL.startswith("postgresql://"):
        print("Error: DATABASE_URL or SUPABASE_DB_URL is not set or invalid.")
        print("Please set DATABASE_URL in your .env or environment variables.")
        print("Example: DATABASE_URL=postgresql://postgres.xxx:password@aws-0-region.pooler.supabase.com:6543/postgres")
        return

    if not os.path.exists(DB_FILE):
        print(f"Error: Local SQLite database file ({DB_FILE}) not found.")
        return

    print("Connecting to local SQLite database...")
    sq_conn = sqlite3.connect(DB_FILE)
    sq_conn.row_factory = sqlite3.Row
    sq_cur = sq_conn.cursor()

    print("Connecting to Supabase PostgreSQL database...")
    pg_conn = psycopg2.connect(DATABASE_URL)
    pg_cur = pg_conn.cursor()

    tables = [
        "users",
        "transactions",
        "budgets",
        "savings_goals",
        "bills",
        "subscriptions",
        "investments",
        "notifications",
        "financial_insights",
        "chat_history"
    ]

    total_migrated = 0
    for table in tables:
        sq_cur.execute(f"SELECT * FROM {table}")
        rows = sq_cur.fetchall()
        if not rows:
            print(f"Skipping empty table '{table}'.")
            continue

        cols = [desc[0] for desc in sq_cur.description]
        col_str = ", ".join(cols)
        val_placeholders = ", ".join(["%s"] * len(cols))

        sql = f"INSERT INTO {table} ({col_str}) VALUES ({val_placeholders}) ON CONFLICT DO NOTHING"

        count = 0
        for r in rows:
            vals = [r[c] for c in cols]
            pg_cur.execute(sql, vals)
            count += 1

        pg_conn.commit()
        print(f"Migrated {count} rows into '{table}'.")
        total_migrated += count

    sq_conn.close()
    pg_conn.close()
    print(f"\nMigration complete! Successfully transferred {total_migrated} records from local SQLite to Supabase PostgreSQL.")

if __name__ == "__main__":
    migrate()
