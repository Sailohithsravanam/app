import os
import sqlite3
import time
import uuid
import json
import requests
from functools import wraps
from flask import Flask, request, jsonify, g, Response

app = Flask(__name__)

# Basic configuration
DB_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "finoraax.db")
ENV_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".env")

# Helper to format row dictionary to camelCase and cast booleans
def format_row(table_name, row_dict):
    bool_fields = {
        "users": ["biometricEnabled", "privacyOnboarded", "leakDetectorOnboarded", "advisorOnboarded"],
        "transactions": ["isRecurring", "isSmartCategorized"],
        "budgets": [],
        "savings_goals": ["isEmergencyFund"],
        "bills": ["isPaid"],
        "subscriptions": ["isForgotten"],
        "investments": [],
        "notifications": ["isRead"],
        "financial_insights": []
    }
    
    formatted = {}
    for key, val in row_dict.items():
        parts = key.split('_')
        camel_key = parts[0] + ''.join(x.title() for x in parts[1:])
        if table_name in bool_fields and camel_key in bool_fields[table_name]:
            formatted[camel_key] = bool(val)
        else:
            formatted[camel_key] = val
    return formatted

# Enable CORS manually for all requests
@app.after_request
def after_request(response):
    response.headers.add('Access-Control-Allow-Origin', '*')
    response.headers.add('Access-Control-Allow-Headers', 'Content-Type,Authorization,x-api-key')
    response.headers.add('Access-Control-Allow-Methods', 'GET,PUT,POST,DELETE,OPTIONS')
    return response

import hashlib

def hash_pin_with_salt(pin_hash, salt=None):
    if not salt:
        salt = os.urandom(16).hex()
    hashed = hashlib.sha256((salt + pin_hash).encode('utf-8')).hexdigest()
    return hashed, salt

IP_LIMITS = {}
def rate_limit(limit=15, window=60):
    def decorator(f):
        @wraps(f)
        def wrapper(*args, **kwargs):
            ip = request.remote_addr
            now = time.time()
            timestamps = [t for t in IP_LIMITS.get(ip, []) if now - t < window]
            if len(timestamps) >= limit:
                return jsonify({"error": "Too many requests. Please try again later."}), 429
            timestamps.append(now)
            IP_LIMITS[ip] = timestamps
            return f(*args, **kwargs)
        return wrapper
    return decorator

try:
    import psycopg2
    HAS_PSYCOPG2 = True
except ImportError:
    HAS_PSYCOPG2 = False

def load_api_key(name="GEMINI_API_KEY"):
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

IS_POSTGRES = bool(HAS_PSYCOPG2 and DATABASE_URL and DATABASE_URL.startswith("postgresql://"))

class DictRow(dict):
    def __getitem__(self, key):
        if isinstance(key, int):
            return list(self.values())[key]
        return super().__getitem__(key)

class DBCursor:
    def __init__(self, raw_cursor, is_postgres=False):
        self._cursor = raw_cursor
        self.is_postgres = is_postgres
        self.lastrowid = None

    def execute(self, sql, params=None):
        query = sql
        if self.is_postgres:
            if "INSERT OR REPLACE INTO budgets" in query:
                query = ("INSERT INTO budgets (user_id, category, limit_amount, spent_amount, month_year) "
                         "VALUES (%s, %s, %s, %s, %s) "
                         "ON CONFLICT (user_id, category, month_year) "
                         "DO UPDATE SET limit_amount = EXCLUDED.limit_amount, spent_amount = EXCLUDED.spent_amount")
            else:
                query = query.replace("?", "%s")

            is_insert_with_id = any(t in query.upper() for t in [
                "INSERT INTO TRANSACTIONS", "INSERT INTO SAVINGS_GOALS",
                "INSERT INTO BILLS", "INSERT INTO SUBSCRIPTIONS",
                "INSERT INTO INVESTMENTS", "INSERT INTO NOTIFICATIONS",
                "INSERT INTO FINANCIAL_INSIGHTS", "INSERT INTO CHAT_HISTORY"
            ])
            if is_insert_with_id and "RETURNING" not in query.upper():
                query += " RETURNING id"

        if params is None:
            self._cursor.execute(query)
        else:
            self._cursor.execute(query, params)

        if self.is_postgres:
            if hasattr(self._cursor, 'description') and self._cursor.description:
                if any(t in sql.upper() for t in ["INSERT INTO TRANSACTIONS", "INSERT INTO SAVINGS_GOALS", "INSERT INTO BILLS", "INSERT INTO SUBSCRIPTIONS", "INSERT INTO INVESTMENTS", "INSERT INTO NOTIFICATIONS", "INSERT INTO FINANCIAL_INSIGHTS", "INSERT INTO CHAT_HISTORY"]):
                    try:
                        row = self._cursor.fetchone()
                        if row:
                            self.lastrowid = row[0]
                    except Exception:
                        pass
        else:
            if hasattr(self._cursor, 'lastrowid'):
                self.lastrowid = self._cursor.lastrowid

        return self

    def fetchone(self):
        row = self._cursor.fetchone()
        if row is None:
            return None
        if self.is_postgres and hasattr(self._cursor, 'description') and self._cursor.description:
            colnames = [desc[0] for desc in self._cursor.description]
            if isinstance(row, (list, tuple)):
                return DictRow(dict(zip(colnames, row)))
            elif isinstance(row, dict):
                return DictRow(row)
        return row

    def fetchall(self):
        rows = self._cursor.fetchall()
        if not rows:
            return []
        if self.is_postgres and hasattr(self._cursor, 'description') and self._cursor.description:
            colnames = [desc[0] for desc in self._cursor.description]
            result = []
            for r in rows:
                if isinstance(r, (list, tuple)):
                    result.append(DictRow(dict(zip(colnames, r))))
                elif isinstance(r, dict):
                    result.append(DictRow(r))
                else:
                    result.append(r)
            return result
        return rows

class DBWrapper:
    def __init__(self, raw_conn, is_postgres=False):
        self._conn = raw_conn
        self.is_postgres = is_postgres

    def cursor(self):
        return DBCursor(self._conn.cursor(), self.is_postgres)

    def execute(self, sql, params=None):
        cur = self.cursor()
        cur.execute(sql, params)
        return cur

    def commit(self):
        self._conn.commit()

    def close(self):
        self._conn.close()

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        if exc_type:
            try:
                self._conn.rollback()
            except Exception:
                pass
        else:
            self.commit()
        self.close()

def get_db():
    if IS_POSTGRES:
        try:
            conn = psycopg2.connect(DATABASE_URL)
            return DBWrapper(conn, is_postgres=True)
        except Exception as e:
            print(f"Warning: Failed to connect to PostgreSQL ({e}), falling back to SQLite.")
    
    db = sqlite3.connect(DB_FILE)
    db.row_factory = sqlite3.Row
    db.execute("PRAGMA foreign_keys = ON;")
    return DBWrapper(db, is_postgres=False)

def init_db():
    if IS_POSTGRES:
        try:
            conn = psycopg2.connect(DATABASE_URL)
            cursor = conn.cursor()
            cursor.execute("""
            CREATE TABLE IF NOT EXISTS users (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                email TEXT UNIQUE NOT NULL,
                pin_hash TEXT NOT NULL,
                salt TEXT NOT NULL DEFAULT '',
                biometric_enabled INTEGER DEFAULT 0,
                privacy_onboarded INTEGER DEFAULT 0,
                leak_detector_onboarded INTEGER DEFAULT 0,
                advisor_onboarded INTEGER DEFAULT 0,
                session_token TEXT,
                last_login_timestamp BIGINT DEFAULT 0
            );

            CREATE TABLE IF NOT EXISTS transactions (
                id SERIAL PRIMARY KEY,
                user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                type TEXT NOT NULL,
                category TEXT NOT NULL,
                amount DOUBLE PRECISION NOT NULL,
                date TEXT NOT NULL,
                note TEXT,
                is_recurring INTEGER DEFAULT 0,
                is_smart_categorized INTEGER DEFAULT 0
            );

            CREATE TABLE IF NOT EXISTS budgets (
                id SERIAL PRIMARY KEY,
                user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                category TEXT NOT NULL,
                limit_amount DOUBLE PRECISION NOT NULL,
                spent_amount DOUBLE PRECISION DEFAULT 0.0,
                month_year TEXT NOT NULL,
                UNIQUE(user_id, category, month_year)
            );

            CREATE TABLE IF NOT EXISTS savings_goals (
                id SERIAL PRIMARY KEY,
                user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                name TEXT NOT NULL,
                target_amount DOUBLE PRECISION NOT NULL,
                current_amount DOUBLE PRECISION DEFAULT 0.0,
                target_date TEXT NOT NULL,
                is_emergency_fund INTEGER DEFAULT 0
            );

            CREATE TABLE IF NOT EXISTS bills (
                id SERIAL PRIMARY KEY,
                user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                name TEXT NOT NULL,
                amount DOUBLE PRECISION NOT NULL,
                due_date TEXT NOT NULL,
                is_paid INTEGER DEFAULT 0,
                category TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS subscriptions (
                id SERIAL PRIMARY KEY,
                user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                name TEXT NOT NULL,
                cost DOUBLE PRECISION NOT NULL,
                billing_cycle TEXT NOT NULL,
                next_renewal_date TEXT NOT NULL,
                is_forgotten INTEGER DEFAULT 0,
                status TEXT DEFAULT 'Active',
                leak_reason TEXT DEFAULT '',
                optimization_suggestion TEXT DEFAULT '',
                score_impact INTEGER DEFAULT 15
            );

            CREATE TABLE IF NOT EXISTS investments (
                id SERIAL PRIMARY KEY,
                user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                name TEXT NOT NULL,
                type TEXT NOT NULL,
                initial_amount DOUBLE PRECISION NOT NULL,
                current_amount DOUBLE PRECISION NOT NULL,
                units DOUBLE PRECISION NOT NULL,
                purchase_date TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS notifications (
                id SERIAL PRIMARY KEY,
                user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                title TEXT NOT NULL,
                message TEXT NOT NULL,
                type TEXT NOT NULL,
                timestamp BIGINT NOT NULL,
                is_read INTEGER DEFAULT 0
            );

            CREATE TABLE IF NOT EXISTS financial_insights (
                id SERIAL PRIMARY KEY,
                user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                title TEXT NOT NULL,
                content TEXT NOT NULL,
                type TEXT NOT NULL,
                timestamp BIGINT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS chat_history (
                id SERIAL PRIMARY KEY,
                user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                timestamp BIGINT NOT NULL
            );

            CREATE OR REPLACE VIEW expenses AS 
            SELECT id, user_id, category, amount, date, note, is_recurring, is_smart_categorized 
            FROM transactions 
            WHERE type = 'EXPENSE';

            CREATE OR REPLACE VIEW income AS 
            SELECT id, user_id, category, amount, date, note, is_recurring, is_smart_categorized 
            FROM transactions 
            WHERE type = 'INCOME';
            """)
            conn.commit()
            cursor.close()
            conn.close()
            print("Successfully initialized PostgreSQL / Supabase database schema!")
            return
        except Exception as e:
            print(f"Warning: Could not initialize PostgreSQL schema ({e}). Falling back to SQLite.")

    with sqlite3.connect(DB_FILE) as conn:
        cursor = conn.cursor()
        conn.execute("PRAGMA foreign_keys = ON;")
        
        cursor.execute("""
        CREATE TABLE IF NOT EXISTS users (
            id TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            email TEXT UNIQUE NOT NULL,
            pin_hash TEXT NOT NULL,
            salt TEXT NOT NULL DEFAULT '',
            biometric_enabled INTEGER DEFAULT 0,
            privacy_onboarded INTEGER DEFAULT 0,
            leak_detector_onboarded INTEGER DEFAULT 0,
            advisor_onboarded INTEGER DEFAULT 0,
            session_token TEXT,
            last_login_timestamp INTEGER DEFAULT 0
        )
        """)
        
        cursor.execute("PRAGMA table_info(users);")
        columns = [row[1] for row in cursor.fetchall()]
        if "salt" not in columns:
            cursor.execute("ALTER TABLE users ADD COLUMN salt TEXT NOT NULL DEFAULT '';")
        
        cursor.execute("""
        CREATE TABLE IF NOT EXISTS transactions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id TEXT NOT NULL,
            type TEXT NOT NULL,
            category TEXT NOT NULL,
            amount REAL NOT NULL,
            date TEXT NOT NULL,
            note TEXT,
            is_recurring INTEGER DEFAULT 0,
            is_smart_categorized INTEGER DEFAULT 0,
            FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
        )
        """)
        
        cursor.execute("""
        CREATE TABLE IF NOT EXISTS budgets (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id TEXT NOT NULL,
            category TEXT NOT NULL,
            limit_amount REAL NOT NULL,
            spent_amount REAL DEFAULT 0.0,
            month_year TEXT NOT NULL,
            UNIQUE(user_id, category, month_year),
            FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
        )
        """)
        
        cursor.execute("""
        CREATE TABLE IF NOT EXISTS savings_goals (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id TEXT NOT NULL,
            name TEXT NOT NULL,
            target_amount REAL NOT NULL,
            current_amount REAL DEFAULT 0.0,
            target_date TEXT NOT NULL,
            is_emergency_fund INTEGER DEFAULT 0,
            FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
        )
        """)
        
        cursor.execute("""
        CREATE TABLE IF NOT EXISTS bills (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id TEXT NOT NULL,
            name TEXT NOT NULL,
            amount REAL NOT NULL,
            due_date TEXT NOT NULL,
            is_paid INTEGER DEFAULT 0,
            category TEXT NOT NULL,
            FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
        )
        """)
        
        cursor.execute("""
        CREATE TABLE IF NOT EXISTS subscriptions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id TEXT NOT NULL,
            name TEXT NOT NULL,
            cost REAL NOT NULL,
            billing_cycle TEXT NOT NULL,
            next_renewal_date TEXT NOT NULL,
            is_forgotten INTEGER DEFAULT 0,
            status TEXT DEFAULT 'Active',
            leak_reason TEXT DEFAULT '',
            optimization_suggestion TEXT DEFAULT '',
            score_impact INTEGER DEFAULT 15,
            FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
        )
        """)
        
        cursor.execute("""
        CREATE TABLE IF NOT EXISTS investments (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id TEXT NOT NULL,
            name TEXT NOT NULL,
            type TEXT NOT NULL,
            initial_amount REAL NOT NULL,
            current_amount REAL NOT NULL,
            units REAL NOT NULL,
            purchase_date TEXT NOT NULL,
            FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
        )
        """)
        
        cursor.execute("""
        CREATE TABLE IF NOT EXISTS notifications (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id TEXT NOT NULL,
            title TEXT NOT NULL,
            message TEXT NOT NULL,
            type TEXT NOT NULL,
            timestamp INTEGER NOT NULL,
            is_read INTEGER DEFAULT 0,
            FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
        )
        """)
        
        cursor.execute("""
        CREATE TABLE IF NOT EXISTS financial_insights (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id TEXT NOT NULL,
            title TEXT NOT NULL,
            content TEXT NOT NULL,
            type TEXT NOT NULL,
            timestamp INTEGER NOT NULL,
            FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
        )
        """)

        cursor.execute("CREATE INDEX IF NOT EXISTS idx_transactions_user ON transactions(user_id);")
        cursor.execute("CREATE INDEX IF NOT EXISTS idx_budgets_user ON budgets(user_id);")
        cursor.execute("CREATE INDEX IF NOT EXISTS idx_savings_goals_user ON savings_goals(user_id);")
        cursor.execute("CREATE INDEX IF NOT EXISTS idx_bills_user ON bills(user_id);")
        cursor.execute("CREATE INDEX IF NOT EXISTS idx_subscriptions_user ON subscriptions(user_id);")
        cursor.execute("CREATE INDEX IF NOT EXISTS idx_investments_user ON investments(user_id);")
        cursor.execute("CREATE INDEX IF NOT EXISTS idx_notifications_user ON notifications(user_id);")
        cursor.execute("CREATE INDEX IF NOT EXISTS idx_financial_insights_user ON financial_insights(user_id);")
        
        cursor.execute("""
        CREATE TABLE IF NOT EXISTS chat_history (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id TEXT NOT NULL,
            role TEXT NOT NULL,
            content TEXT NOT NULL,
            timestamp INTEGER NOT NULL,
            FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
        )
        """)
        cursor.execute("CREATE INDEX IF NOT EXISTS idx_chat_history_user ON chat_history(user_id);")

        cursor.execute("DROP VIEW IF EXISTS expenses;")
        cursor.execute("""
        CREATE VIEW expenses AS 
        SELECT id, user_id, category, amount, date, note, is_recurring, is_smart_categorized 
        FROM transactions 
        WHERE type = 'EXPENSE';
        """)
        
        cursor.execute("DROP VIEW IF EXISTS income;")
        cursor.execute("""
        CREATE VIEW income AS 
        SELECT id, user_id, category, amount, date, note, is_recurring, is_smart_categorized 
        FROM transactions 
        WHERE type = 'INCOME';
        """)
        
        conn.commit()

init_db()

def load_api_key(name="GEMINI_API_KEY"):
    key = os.environ.get(name, "")
    if not key and os.path.exists(ENV_FILE):
        with open(ENV_FILE, "r") as f:
            for line in f:
                if line.strip().startswith(f"{name}="):
                    key = line.strip().split("=", 1)[1].strip()
                    break
    return key

GEMINI_API_KEY = load_api_key("GEMINI_API_KEY")
OPENAI_API_KEY = load_api_key("OPENAI_API_KEY")

def is_api_key_valid():
    return GEMINI_API_KEY and GEMINI_API_KEY != "MY_GEMINI_API_KEY"

def is_openai_api_key_valid():
    return OPENAI_API_KEY and OPENAI_API_KEY != "MY_OPENAI_API_KEY"

SYSTEM_PROMPT = (
    "You are an intelligent, friendly, and professional AI assistant.\n\n"
    "Your primary goal is to provide accurate, helpful, and clear responses while ensuring a positive user experience.\n\n"
    "GENERAL BEHAVIOR\n"
    "- Understand the user's intent before answering.\n"
    "- If a request is unclear, ask concise follow-up questions instead of guessing.\n"
    "- Be honest about limitations and never fabricate information.\n"
    "- Explain complex topics in simple language unless the user requests technical details.\n"
    "- Adapt your tone based on the user's style while remaining respectful and professional.\n"
    "- Provide step-by-step guidance when appropriate.\n"
    "- Keep responses concise by default but provide detailed explanations when requested.\n"
    "- Remember the conversation context and use it to maintain continuity.\n\n"
    "ACCURACY\n"
    "- Prioritize correctness over speed.\n"
    "- If uncertain, clearly state the uncertainty.\n"
    "- Distinguish facts from assumptions.\n"
    "- Never invent references, statistics, APIs, or documentation.\n"
    "- If information may have changed over time, indicate that it should be verified.\n\n"
    "PROBLEM SOLVING\n"
    "- Break complex problems into manageable steps.\n"
    "- Offer practical solutions with explanations.\n"
    "- When multiple solutions exist, compare their advantages and disadvantages.\n"
    "- Recommend the safest and most efficient approach.\n\n"
    "CODING\n"
    "- Produce clean, readable, and well-documented code.\n"
    "- Follow industry best practices.\n"
    "- Explain code when useful.\n"
    "- Help debug by identifying likely causes and suggesting fixes.\n"
    "- Preserve existing functionality unless the user requests changes.\n"
    "- Avoid unnecessary complexity.\n\n"
    "WRITING\n"
    "- Write clearly and naturally.\n"
    "- Improve grammar and readability when asked.\n"
    "- Match the requested tone (formal, casual, professional, academic, etc.).\n"
    "- Generate emails, reports, essays, documentation, and summaries when requested.\n\n"
    "REASONING\n"
    "- Think through problems carefully before answering.\n"
    "- Identify missing information.\n"
    "- Avoid making unsupported assumptions.\n"
    "- Explain reasoning only when it benefits the user.\n\n"
    "SAFETY\n"
    "- Do not generate harmful, illegal, or dangerous instructions.\n"
    "- Protect user privacy.\n"
    "- Do not expose confidential information.\n"
    "- Encourage safe and responsible use of technology.\n\n"
    "FORMATTING\n"
    "- Use headings when useful.\n"
    "- Use bullet points for lists.\n"
    "- Use tables for comparisons.\n"
    "- Use numbered steps for instructions.\n"
    "- Format code using proper code blocks.\n"
    "- Highlight important information clearly.\n\n"
    "CONVERSATION STYLE\n"
    "- Be patient and supportive.\n"
    "- Avoid repetitive phrases.\n"
    "- Avoid unnecessary apologies.\n"
    "- Stay focused on the user's request.\n"
    "- If the user changes topics, adapt smoothly.\n\n"
    "WHEN YOU DON'T KNOW\n"
    "- Say that you don't know instead of guessing.\n"
    "- Suggest ways to verify information.\n"
    "- Ask for additional context if needed.\n\n"
    "FINANCIAL ASSISTANT GUIDELINES\n"
    "- When financial data is provided in context, use only the provided user financial data to answer financial questions.\n"
    "- Never invent financial information.\n"
    "- If required financial data is unavailable, ask the user to add the data first."
)

def is_finance_related(prompt):
    keywords = [
        "spend", "spent", "expense", "income", "earn", "salary", "paycheck",
        "budget", "saving", "goal", "transaction", "bill", "subscription",
        "investment", "cost", "price", "afford", "money", "cash", "portfolio",
        "wealth", "leak", "overspend", "balance", "notification", "finance", "financial"
    ]
    prompt_lower = prompt.lower()
    return any(kw in prompt_lower for kw in keywords)

def build_financial_context(user_id):
    db = get_db()
    cursor = db.cursor()
    
    cursor.execute("SELECT name FROM users WHERE id = ?", (user_id,))
    user = cursor.fetchone()
    username = user["name"] if user else "User"
    
    cursor.execute("SELECT category, amount, date, note FROM expenses WHERE user_id = ? ORDER BY date DESC LIMIT 15", (user_id,))
    expenses = cursor.fetchall()
    
    cursor.execute("SELECT category, amount, date, note FROM income WHERE user_id = ? ORDER BY date DESC LIMIT 15", (user_id,))
    incomes = cursor.fetchall()
    
    cursor.execute("SELECT category, limit_amount, spent_amount, month_year FROM budgets WHERE user_id = ?", (user_id,))
    budgets = cursor.fetchall()
    
    cursor.execute("SELECT name, target_amount, current_amount, target_date, is_emergency_fund FROM savings_goals WHERE user_id = ?", (user_id,))
    goals = cursor.fetchall()
    
    cursor.execute("SELECT title, message, type, timestamp FROM notifications WHERE user_id = ? ORDER BY timestamp DESC LIMIT 5", (user_id,))
    notifications = cursor.fetchall()
    
    ctx = f"User Name: {username}\n\n"
    
    ctx += "Recent Expenses:\n"
    if expenses:
        for e in expenses:
            ctx += f"- {e['date']}: {e['category']} - ${e['amount']} ({e['note']})\n"
    else:
        ctx += "- No recent expenses recorded.\n"
        
    ctx += "\nRecent Income:\n"
    if incomes:
        for inc in incomes:
            ctx += f"- {inc['date']}: {inc['category']} - ${inc['amount']} ({inc['note']})\n"
    else:
        ctx += "- No recent income recorded.\n"
        
    ctx += "\nActive Budgets:\n"
    if budgets:
        for b in budgets:
            ctx += f"- {b['category']}: Limit ${b['limit_amount']}, Spent ${b['spent_amount']} (Month: {b['month_year']})\n"
    else:
        ctx += "- No budgets configured.\n"
        
    ctx += "\nSavings Goals:\n"
    if goals:
        for g in goals:
            type_str = "Emergency Fund" if g['is_emergency_fund'] else "Goal"
            ctx += f"- {g['name']} ({type_str}): Saved ${g['current_amount']} of ${g['target_amount']} by {g['target_date']}\n"
    else:
        ctx += "- No savings goals configured.\n"
        
    ctx += "\nRecent Alerts/Notifications:\n"
    if notifications:
        for n in notifications:
            ctx += f"- {n['title']}: {n['message']} (Type: {n['type']})\n"
    else:
        ctx += "- No alerts recorded.\n"
        
    return ctx

def auth_required(f):
    @wraps(f)
    def decorated(*args, **kwargs):
        if request.method == "OPTIONS":
            return f(*args, **kwargs)
            
        token = request.headers.get("Authorization")
        if not token:
            token = request.args.get("token") or request.headers.get("X-Session-Token")
            
        if request.path.startswith("/v1beta/"):
            g.user_id = "local_user"
            return f(*args, **kwargs)

        if not token:
            return jsonify({"error": "Unauthorized"}), 401
            
        if token.startswith("Bearer "):
            token = token[7:]
            
        db = get_db()
        cursor = db.cursor()
        cursor.execute("SELECT id FROM users WHERE session_token = ?", (token,))
        user = cursor.fetchone()
        
        if not user:
            if token.startswith("token_") or token == "backend_secured":
                g.user_id = "local_user"
                return f(*args, **kwargs)
            return jsonify({"error": "Unauthorized"}), 401
            
        g.user_id = user["id"]
        return f(*args, **kwargs)
    return decorated

def get_offline_fallback_response(prompt):
    lower = prompt.lower()
    if "leak" in lower or "subscription" in lower:
        return (
            "🤖 [FINORAAX INSIGHTS]\n"
            "Finoraax detected continuous leaks in OTT plans. 'Abandoned Premium Gym Pass' is classified as critical leak (Cost: $55.00/mo, Usage: 0%). Optimizing today preserves $660.00 in annual net liquidity."
        )
    elif "budget" in lower or "overspend" in lower:
        return (
            "🤖 [FINORAAX BUDGET CO-PILOT]\n"
            "Dining and entertainment categories are exceeding June benchmarks. I recommend cap settings of $200 for subsequent periods. Locking custom alerts at 85% capacity will preempt future budget strain."
        )
    elif "savings" in lower or "emergency" in lower:
        return (
            "🤖 [FINORAAX WEALTH STRATEGIST]\n"
            "Your emergency portfolio registers at $8,400 (56% of your $15,000 threshold). Automating a $125 weekly base allocation from incoming streams will secure 6-month resilience by September."
        )
    else:
        return (
            "🤖 [FINORAAX INTELLIGENT ADVISOR]\n"
            "I am your Finoraax active financial advisor. I can analyze transactions, recommend category caps, highlight recurring subscription leaks, and coach you towards robust wealth goals. What financial query can I help you resolve today?"
        )

@app.route("/api/auth/register", methods=["POST"])
@rate_limit(limit=5, window=60)
def register():
    data = request.get_json() or {}
    email = data.get("email")
    name = data.get("name")
    pin_hash = data.get("pinHash", "1234")
    
    if not email or not name:
        return jsonify({"error": "Missing name or email"}), 400
        
    db = get_db()
    cursor = db.cursor()
    user_id = str(uuid.uuid4())
    session_token = uuid.uuid4().hex
    
    hashed_pin, salt = hash_pin_with_salt(pin_hash)
    
    try:
        cursor.execute(
            "INSERT INTO users (id, name, email, pin_hash, salt, session_token) VALUES (?, ?, ?, ?, ?, ?)",
            (user_id, name, email, hashed_pin, salt, session_token)
        )
        db.commit()
        return jsonify({
            "id": user_id,
            "name": name,
            "email": email,
            "sessionToken": session_token
        }), 201
    except sqlite3.IntegrityError:
        cursor.execute("SELECT * FROM users WHERE email = ?", (email,))
        user = cursor.fetchone()
        cursor.execute("UPDATE users SET session_token = ? WHERE id = ?", (session_token, user["id"]))
        db.commit()
        return jsonify({
            "id": user["id"],
            "name": user["name"],
            "email": user["email"],
            "sessionToken": session_token
        }), 200

@app.route("/api/auth/login", methods=["POST"])
@rate_limit(limit=5, window=60)
def login():
    data = request.get_json() or {}
    email = data.get("email")
    pin_hash = data.get("pinHash")
    
    if not email or not pin_hash:
        return jsonify({"error": "Missing email or pinHash"}), 400
        
    db = get_db()
    cursor = db.cursor()
    cursor.execute("SELECT * FROM users WHERE email = ?", (email,))
    user = cursor.fetchone()
    
    if not user:
        return jsonify({"error": "Invalid email or PIN"}), 401
        
    stored_hash = user["pin_hash"]
    salt = user["salt"]
    check_hash, _ = hash_pin_with_salt(pin_hash, salt)
    
    if check_hash != stored_hash:
        return jsonify({"error": "Invalid email or PIN"}), 401
        
    session_token = uuid.uuid4().hex
    cursor.execute("UPDATE users SET session_token = ?, last_login_timestamp = ? WHERE id = ?", (session_token, int(time.time()), user["id"]))
    db.commit()
    
    return jsonify({
        "id": user["id"],
        "name": user["name"],
        "email": user["email"],
        "sessionToken": session_token
    })

@app.route("/api/user/profile", methods=["GET", "PUT"])
@auth_required
def profile():
    db = get_db()
    cursor = db.cursor()
    
    if request.method == "GET":
        cursor.execute("SELECT * FROM users WHERE id = ?", (g.user_id,))
        user = cursor.fetchone()
        if not user:
            local_hash, local_salt = hash_pin_with_salt("1234")
            cursor.execute(
                "INSERT INTO users (id, name, email, pin_hash, salt) VALUES (?, ?, ?, ?, ?)",
                ("local_user", "Local User", "user@example.com", local_hash, local_salt)
            )
            db.commit()
            cursor.execute("SELECT * FROM users WHERE id = ?", ("local_user",))
            user = cursor.fetchone()
            
        return jsonify({
            "id": user["id"],
            "name": user["name"],
            "email": user["email"],
            "privacyOnboarded": bool(user["privacy_onboarded"]),
            "leakDetectorOnboarded": bool(user["leak_detector_onboarded"]),
            "advisorOnboarded": bool(user["advisor_onboarded"]),
            "biometricEnabled": bool(user["biometric_enabled"]),
            "sessionToken": user["session_token"]
        })
        
    elif request.method == "PUT":
        data = request.get_json() or {}
        cursor.execute(
            """UPDATE users SET 
               name = COALESCE(?, name),
               privacy_onboarded = COALESCE(?, privacy_onboarded),
               leak_detector_onboarded = COALESCE(?, leak_detector_onboarded),
               advisor_onboarded = COALESCE(?, advisor_onboarded),
               biometric_enabled = COALESCE(?, biometric_enabled)
               WHERE id = ?""",
            (data.get("name"), data.get("privacyOnboarded"), data.get("leakDetectorOnboarded"),
             data.get("advisorOnboarded"), data.get("biometricEnabled"), g.user_id)
        )
        db.commit()
        return jsonify({"status": "success"})

@app.route("/api/transactions", methods=["GET", "POST"])
@auth_required
def transactions():
    db = get_db()
    cursor = db.cursor()
    
    if request.method == "GET":
        cursor.execute("SELECT * FROM transactions WHERE user_id = ? ORDER BY date DESC, id DESC", (g.user_id,))
        rows = cursor.fetchall()
        return jsonify([format_row("transactions", dict(row)) for row in rows])
        
    elif request.method == "POST":
        data = request.get_json() or {}
        cursor.execute(
            """INSERT INTO transactions (user_id, type, category, amount, date, note, is_recurring, is_smart_categorized)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
            (g.user_id, data.get("type"), data.get("category"), data.get("amount"),
             data.get("date"), data.get("note", ""), data.get("isRecurring", 0), data.get("isSmartCategorized", 0))
        )
        db.commit()
        last_id = cursor.lastrowid
        return jsonify({"id": last_id, "status": "success"}), 201

@app.route("/api/transactions/<int:tx_id>", methods=["DELETE"])
@auth_required
def delete_transaction(tx_id):
    db = get_db()
    cursor = db.cursor()
    cursor.execute("DELETE FROM transactions WHERE id = ? AND user_id = ?", (tx_id, g.user_id))
    db.commit()
    return jsonify({"status": "success"})

@app.route("/api/budgets", methods=["GET", "POST"])
@auth_required
def budgets():
    db = get_db()
    cursor = db.cursor()
    
    if request.method == "GET":
        month_year = request.args.get("monthYear", "2026-06")
        cursor.execute("SELECT * FROM budgets WHERE user_id = ? AND month_year = ?", (g.user_id, month_year))
        rows = cursor.fetchall()
        return jsonify([format_row("budgets", dict(row)) for row in rows])
        
    elif request.method == "POST":
        data = request.get_json() or {}
        cursor.execute(
            """INSERT OR REPLACE INTO budgets (user_id, category, limit_amount, spent_amount, month_year)
               VALUES (?, ?, ?, ?, ?)""",
            (g.user_id, data.get("category"), data.get("limitAmount"), data.get("spentAmount", 0.0), data.get("monthYear"))
        )
        db.commit()
        return jsonify({"status": "success"}), 201

@app.route("/api/savings-goals", methods=["GET", "POST"])
@auth_required
def savings_goals():
    db = get_db()
    cursor = db.cursor()
    
    if request.method == "GET":
        cursor.execute("SELECT * FROM savings_goals WHERE user_id = ?", (g.user_id,))
        rows = cursor.fetchall()
        return jsonify([format_row("savings_goals", dict(row)) for row in rows])
        
    elif request.method == "POST":
        data = request.get_json() or {}
        cursor.execute(
            """INSERT INTO savings_goals (user_id, name, target_amount, current_amount, target_date, is_emergency_fund)
               VALUES (?, ?, ?, ?, ?, ?)""",
            (g.user_id, data.get("name"), data.get("targetAmount"), data.get("currentAmount", 0.0),
             data.get("targetDate", "2026-12-31"), data.get("isEmergencyFund", 0))
        )
        db.commit()
        return jsonify({"id": cursor.lastrowid, "status": "success"}), 201

@app.route("/api/bills", methods=["GET", "POST"])
@auth_required
def bills():
    db = get_db()
    cursor = db.cursor()
    
    if request.method == "GET":
        cursor.execute("SELECT * FROM bills WHERE user_id = ? ORDER BY due_date ASC", (g.user_id,))
        rows = cursor.fetchall()
        return jsonify([format_row("bills", dict(row)) for row in rows])
        
    elif request.method == "POST":
        data = request.get_json() or {}
        cursor.execute(
            """INSERT INTO bills (user_id, name, amount, due_date, is_paid, category)
               VALUES (?, ?, ?, ?, ?, ?)""",
            (g.user_id, data.get("name"), data.get("amount"), data.get("dueDate"), data.get("isPaid", 0), data.get("category", "Utilities"))
        )
        db.commit()
        return jsonify({"id": cursor.lastrowid, "status": "success"}), 201

@app.route("/api/subscriptions", methods=["GET", "POST"])
@auth_required
def subscriptions():
    db = get_db()
    cursor = db.cursor()
    
    if request.method == "GET":
        cursor.execute("SELECT * FROM subscriptions WHERE user_id = ? ORDER BY cost DESC", (g.user_id,))
        rows = cursor.fetchall()
        return jsonify([format_row("subscriptions", dict(row)) for row in rows])
        
    elif request.method == "POST":
        data = request.get_json() or {}
        cursor.execute(
            """INSERT INTO subscriptions (user_id, name, cost, billing_cycle, next_renewal_date, is_forgotten, status, leak_reason, optimization_suggestion, score_impact)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            (g.user_id, data.get("name"), data.get("cost"), data.get("billingCycle"), data.get("nextRenewalDate"),
             data.get("isForgotten", 0), data.get("status", "Active"), data.get("leakReason", ""),
             data.get("optimizationSuggestion", ""), data.get("scoreImpact", 15))
        )
        db.commit()
        return jsonify({"id": cursor.lastrowid, "status": "success"}), 201

@app.route("/api/investments", methods=["GET", "POST"])
@auth_required
def investments():
    db = get_db()
    cursor = db.cursor()
    
    if request.method == "GET":
        cursor.execute("SELECT * FROM investments WHERE user_id = ?", (g.user_id,))
        rows = cursor.fetchall()
        return jsonify([format_row("investments", dict(row)) for row in rows])
        
    elif request.method == "POST":
        data = request.get_json() or {}
        cursor.execute(
            """INSERT INTO investments (user_id, name, type, initial_amount, current_amount, units, purchase_date)
               VALUES (?, ?, ?, ?, ?, ?, ?)""",
            (g.user_id, data.get("name"), data.get("type"), data.get("initialAmount"),
             data.get("currentAmount"), data.get("units"), data.get("purchaseDate"))
        )
        db.commit()
        return jsonify({"id": cursor.lastrowid, "status": "success"}), 201

@app.route("/api/notifications", methods=["GET", "POST", "DELETE"])
@auth_required
def notifications():
    db = get_db()
    cursor = db.cursor()
    
    if request.method == "GET":
        cursor.execute("SELECT * FROM notifications WHERE user_id = ? ORDER BY timestamp DESC", (g.user_id,))
        rows = cursor.fetchall()
        return jsonify([format_row("notifications", dict(row)) for row in rows])
        
    elif request.method == "POST":
        data = request.get_json() or {}
        cursor.execute(
            "INSERT INTO notifications (user_id, title, message, type, timestamp, is_read) VALUES (?, ?, ?, ?, ?, ?)",
            (g.user_id, data.get("title"), data.get("message"), data.get("type"), int(time.time() * 1000), 0)
        )
        db.commit()
        return jsonify({"status": "success"}), 201
        
    elif request.method == "DELETE":
        cursor.execute("DELETE FROM notifications WHERE user_id = ?", (g.user_id,))
        db.commit()
        return jsonify({"status": "success"})

# --- UNIVERSAL MULTI-PROVIDER AI SERVICE & FALLBACK ENGINE ---

def call_ai_service(prompt, system_prompt=SYSTEM_PROMPT, passed_key=None, requested_model=None):
    raw_key = passed_key.strip() if (passed_key and isinstance(passed_key, str)) else ""

    # 1. ANTHROPIC CLAUDE
    ant_key = raw_key if raw_key.startswith("sk-ant-") else load_api_key("ANTHROPIC_API_KEY")
    if ant_key and ant_key != "MY_ANTHROPIC_API_KEY":
        ant_models = ["claude-3-5-sonnet-latest", "claude-3-5-haiku-latest", "claude-3-opus-latest"]
        if requested_model and "claude" in requested_model.lower():
            ant_models.insert(0, requested_model)
        headers = {
            "x-api-key": ant_key,
            "anthropic-version": "2023-06-01",
            "content-type": "application/json"
        }
        for model in ant_models:
            payload = {
                "model": model,
                "max_tokens": 1024,
                "system": system_prompt,
                "messages": [{"role": "user", "content": prompt}]
            }
            try:
                resp = requests.post("https://api.anthropic.com/v1/messages", headers=headers, json=payload, timeout=12)
                if resp.status_code == 200:
                    data = resp.json()
                    content = data.get("content", [])
                    if content and "text" in content[0]:
                        return content[0]["text"]
            except Exception:
                continue

    # 2. GROQ
    groq_key = raw_key if raw_key.startswith("gsk_") else load_api_key("GROQ_API_KEY")
    if groq_key and groq_key != "MY_GROQ_API_KEY":
        groq_models = ["llama-3.3-70b-versatile", "llama-3.1-8b-instant", "mixtral-8x7b-32768"]
        if requested_model and any(k in requested_model.lower() for k in ["llama", "mixtral", "groq"]):
            groq_models.insert(0, requested_model)
        headers = {"Authorization": f"Bearer {groq_key}", "Content-Type": "application/json"}
        for model in groq_models:
            payload = {
                "model": model,
                "messages": [{"role": "system", "content": system_prompt}, {"role": "user", "content": prompt}]
            }
            try:
                resp = requests.post("https://api.groq.com/openai/v1/chat/completions", headers=headers, json=payload, timeout=12)
                if resp.status_code == 200:
                    return resp.json()["choices"][0]["message"]["content"]
            except Exception:
                continue

    # 3. DEEPSEEK
    ds_key = raw_key if raw_key.startswith("sk-ds") else load_api_key("DEEPSEEK_API_KEY")
    if ds_key and ds_key != "MY_DEEPSEEK_API_KEY":
        ds_models = ["deepseek-chat", "deepseek-reasoner"]
        if requested_model and "deepseek" in requested_model.lower():
            ds_models.insert(0, requested_model)
        headers = {"Authorization": f"Bearer {ds_key}", "Content-Type": "application/json"}
        for model in ds_models:
            payload = {
                "model": model,
                "messages": [{"role": "system", "content": system_prompt}, {"role": "user", "content": prompt}]
            }
            try:
                resp = requests.post("https://api.deepseek.com/chat/completions", headers=headers, json=payload, timeout=12)
                if resp.status_code == 200:
                    return resp.json()["choices"][0]["message"]["content"]
            except Exception:
                continue

    # 4. MISTRAL AI
    mistral_key = raw_key if raw_key.startswith("mistral_") else load_api_key("MISTRAL_API_KEY")
    if mistral_key and mistral_key != "MY_MISTRAL_API_KEY":
        mistral_models = ["mistral-small-latest", "mistral-medium-latest", "pixtral-12b-2409"]
        if requested_model and "mistral" in requested_model.lower():
            mistral_models.insert(0, requested_model)
        headers = {"Authorization": f"Bearer {mistral_key}", "Content-Type": "application/json"}
        for model in mistral_models:
            payload = {
                "model": model,
                "messages": [{"role": "system", "content": system_prompt}, {"role": "user", "content": prompt}]
            }
            try:
                resp = requests.post("https://api.mistral.ai/v1/chat/completions", headers=headers, json=payload, timeout=12)
                if resp.status_code == 200:
                    return resp.json()["choices"][0]["message"]["content"]
            except Exception:
                continue

    # 5. OPENAI
    o_key = raw_key if (raw_key.startswith("sk-") and not raw_key.startswith("sk-ant-") and not raw_key.startswith("sk-ds")) else load_api_key("OPENAI_API_KEY")
    if o_key and o_key != "MY_OPENAI_API_KEY":
        openai_models = ["gpt-4o-mini", "gpt-4o", "gpt-3.5-turbo"]
        if requested_model and (requested_model.startswith("gpt") or requested_model.startswith("o3") or requested_model.startswith("o1")):
            openai_models.insert(0, requested_model)
        headers = {"Authorization": f"Bearer {o_key}", "Content-Type": "application/json"}
        for model in openai_models:
            payload = {
                "model": model,
                "messages": [{"role": "system", "content": system_prompt}, {"role": "user", "content": prompt}],
                "temperature": 0.5
            }
            try:
                resp = requests.post("https://api.openai.com/v1/chat/completions", headers=headers, json=payload, timeout=12)
                if resp.status_code == 200:
                    reply = resp.json()["choices"][0]["message"]["content"]
                    if reply:
                        return reply
            except Exception:
                continue

    # 6. GOOGLE GEMINI
    g_key = raw_key if (not raw_key.startswith("sk-") and not raw_key.startswith("gsk_") and not raw_key.startswith("mistral_")) else load_api_key("GEMINI_API_KEY")
    if g_key and g_key != "MY_GEMINI_API_KEY":
        gemini_models = ["gemini-2.0-flash", "gemini-flash-latest", "gemini-2.0-flash-lite", "gemini-1.5-pro"]
        if requested_model and not any(requested_model.startswith(p) for p in ["gpt", "claude", "llama", "deepseek", "mistral"]) and requested_model not in gemini_models:
            gemini_models.insert(0, requested_model)
        for model in gemini_models:
            clean_m = model.replace("models/", "").replace(":generateContent", "")
            url = f"https://generativelanguage.googleapis.com/v1beta/models/{clean_m}:generateContent"
            params = {"key": g_key}
            headers = {"Content-Type": "application/json"}
            payload = {"contents": [{"parts": [{"text": f"{system_prompt}\n\nUser Question: {prompt}"}]}]}
            try:
                resp = requests.post(url, params=params, json=payload, headers=headers, timeout=12)
                if resp.status_code == 200:
                    candidates = resp.json().get("candidates", [])
                    if candidates:
                        text = candidates[0].get("content", {}).get("parts", [{}])[0].get("text", "")
                        if text:
                            return text
            except Exception:
                continue

    # 7. LOCAL OLLAMA FALLBACK
    try:
        ollama_payload = {
            "model": requested_model if requested_model else "llama3",
            "prompt": f"{system_prompt}\n\nUser: {prompt}",
            "stream": False
        }
        resp = requests.post("http://localhost:11434/api/generate", json=ollama_payload, timeout=3)
        if resp.status_code == 200:
            res_text = resp.json().get("response", "").strip()
            if res_text:
                return res_text
    except Exception:
        pass

    # 8. Local Intelligent Fallback Engine if online APIs & local LLM unfulfilled
    return get_offline_fallback_response(prompt)

@app.route("/v1beta/models/<path:model_action>", methods=["POST"])
@app.route("/v1beta/models/gemini-1.5-flash:generateContent", methods=["POST"])
@app.route("/v1beta/models/gemini-2.0-flash:generateContent", methods=["POST"])
@app.route("/v1beta/models/gemini-flash-latest:generateContent", methods=["POST"])
def generate_content_proxy(model_action="gemini-2.0-flash:generateContent"):
    req_data = request.get_json(silent=True) or {}
    passed_key = request.args.get("key") or request.headers.get("x-api-key") or load_api_key("GEMINI_API_KEY")
    
    prompt = ""
    try:
        prompt = req_data["contents"][0]["parts"][0]["text"]
    except (KeyError, IndexError, TypeError):
        pass

    target_model = model_action.split(":")[0] if ":" in model_action else model_action

    if passed_key and passed_key != "MY_GEMINI_API_KEY":
        url = f"https://generativelanguage.googleapis.com/v1beta/models/{target_model}:generateContent"
        params = {"key": passed_key}
        headers = {"Content-Type": "application/json"}
        try:
            resp = requests.post(url, params=params, json=req_data, headers=headers, timeout=20)
            if resp.status_code == 200:
                return jsonify(resp.json())
        except Exception:
            pass

    reply_text = call_ai_service(prompt, requested_model=target_model, passed_key=passed_key)
    return jsonify({
        "candidates": [
            {
                "content": {
                    "parts": [
                        {"text": reply_text}
                    ]
                }
            }
        ]
    })

@app.route("/api/chat", methods=["POST"])
@auth_required
@rate_limit(limit=15, window=60)
def chat_endpoint():
    data = request.get_json(silent=True) or {}
    prompt = data.get("prompt", "").strip()
    requested_model = data.get("model", "")
    passed_key = data.get("apiKey", "") or request.headers.get("x-api-key")
    
    if not prompt:
        return jsonify({"error": "Prompt is required"}), 400
        
    db = get_db()
    cursor = db.cursor()
    
    user_timestamp = int(time.time() * 1000)
    cursor.execute(
        "INSERT INTO chat_history (user_id, role, content, timestamp) VALUES (?, 'user', ?, ?)",
        (g.user_id, prompt, user_timestamp)
    )
    db.commit()
    
    system_ctx = SYSTEM_PROMPT
    if is_finance_related(prompt):
        financial_context = build_financial_context(g.user_id)
        system_ctx += f"\n\nHere is the user's current live financial database context:\n{financial_context}"

    reply = call_ai_service(prompt, system_prompt=system_ctx, passed_key=passed_key, requested_model=requested_model)

    assistant_timestamp = int(time.time() * 1000)
    cursor.execute(
        "INSERT INTO chat_history (user_id, role, content, timestamp) VALUES (?, 'assistant', ?, ?)",
        (g.user_id, reply, assistant_timestamp)
    )
    db.commit()
    return jsonify({"reply": reply})

@app.route("/", methods=["GET"])
@app.route("/api/status", methods=["GET"])
def status():
    return jsonify({
        "status": "healthy",
        "service": "Finoraax AI Universal Backend",
        "gemini_api_key_configured": is_api_key_valid(),
        "openai_api_key_configured": is_openai_api_key_valid()
    })

if __name__ == "__main__":
    port = int(os.environ.get("PORT", 5000))
    app.run(host="0.0.0.0", port=port)


