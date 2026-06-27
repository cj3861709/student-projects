# ==================== 导入模块 ====================
from flask import Flask, jsonify, render_template, request
from flask_cors import CORS   # 解决跨域问题
import mysql.connector        # MySQL 连接库
import pickle                 # 加载模型

# ==================== 创建 Flask 应用 ====================
app = Flask(__name__)
CORS(app)   # 允许跨域请求

# ==================== MySQL 数据库配置 ====================
DB_CONFIG = {
    'host': 'localhost',
    'user': 'root',
    'password': '123456',   # 实际使用时请修改为真实密码
    'database': 'stock_db',
    'auth_plugin': 'mysql_native_password'
}

# ==================== 加载预测模型 ====================
try:
    with open('/tmp/stock_xgb_model.pkl', 'rb') as f:
        model = pickle.load(f)
    print("Model loaded")
except:
    model = None
    print("No model found")

# ==================== 数据库连接辅助函数 ====================
def get_db_connection():
    """返回 MySQL 数据库连接对象"""
    return mysql.connector.connect(**DB_CONFIG)

# ==================== 路由：首页 ====================
@app.route('/')
def index():
    """返回前端页面"""
    return render_template('index.html')

# ==================== 路由：获取 K 线数据 ====================
@app.route('/api/kline/<stock_code>')
def kline(stock_code):
    """
    根据股票代码返回日线 K 线数据（最近 days 条）
    参数：stock_code, days（默认30）
    返回：JSON 数组，每个元素包含 time, open, high, low, close, volume
    """
    days = request.args.get('days', 30, int)
    conn = get_db_connection()
    cursor = conn.cursor(dictionary=True)
    cursor.execute("""
        SELECT start_time, open_price, high_price, low_price, close_price, volume
        FROM realtime_klines
        WHERE stock_code = %s
        ORDER BY start_time DESC
        LIMIT %s
    """, (stock_code, days))
    rows = cursor.fetchall()
    cursor.close()
    conn.close()
    # 按时间升序返回（前端 ECharts 需要正序）
    data = [{
        'time': r['start_time'].isoformat(),
        'open': float(r['open_price']),
        'high': float(r['high_price']),
        'low': float(r['low_price']),
        'close': float(r['close_price']),
        'volume': int(r['volume'])
    } for r in rows]
    return jsonify(data[::-1])

# ==================== 路由：获取预测结果 ====================
@app.route('/api/prediction/<stock_code>')
def prediction(stock_code):
    """
    返回指定股票的最新预测结果（方向、置信度、预测时间）
    """
    conn = get_db_connection()
    cursor = conn.cursor(dictionary=True)
    cursor.execute("""
        SELECT predicted_direction, confidence, predict_time
        FROM predictions
        WHERE stock_code = %s
        ORDER BY predict_time DESC
        LIMIT 1
    """, (stock_code,))
    row = cursor.fetchone()
    cursor.close()
    conn.close()
    return jsonify(row or {})

# ==================== 路由：获取新闻情感数据 ====================
@app.route('/api/sentiment/<stock_code>')
def sentiment(stock_code):
    """
    返回最近20条新闻的情感得分、标题和发布时间
    """
    conn = get_db_connection()
    cursor = conn.cursor(dictionary=True)
    cursor.execute("""
        SELECT publish_time, sentiment_score, title
        FROM news_sentiment_mysql
        WHERE stock_code = %s
        ORDER BY publish_time DESC
        LIMIT 20
    """, (stock_code,))
    rows = cursor.fetchall()
    cursor.close()
    conn.close()
    return jsonify(rows)

# ==================== 启动服务器 ====================
if __name__ == '__main__':
    # 监听所有网络接口，端口 8080，开启调试模式
    app.run(host='0.0.0.0', port=8080, debug=True)