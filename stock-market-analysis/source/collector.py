#!/usr/bin/env python3
# ==================== 模块导入 ====================
import baostock as bs          # 免费 A 股数据接口，获取历史日线
import akshare as ak           # 财经数据接口，获取实时行情和新闻
import time                    # 时间控制，用于延迟
import json                    # JSON 序列化，用于 Kafka 消息
import random                  # 随机延迟，模拟人类行为
import pandas as pd            # 数据处理，将返回数据转为 DataFrame
from kafka import KafkaProducer  # Kafka 生产者，发送消息
from datetime import datetime, timedelta  # 时间处理
import logging                 # 日志记录
# 重试库，自动处理网络异常
from tenacity import retry, stop_after_attempt, wait_exponential, retry_if_exception_type

# ==================== 日志配置 ====================
# 设置日志级别为 INFO，输出格式包含时间、等级和消息
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

# ==================== Kafka 生产者配置 ====================
# 创建 Kafka 生产者，连接本地 9092 端口，消息使用 JSON 格式编码
producer = KafkaProducer(
    bootstrap_servers='localhost:9092',
    value_serializer=lambda v: json.dumps(v, ensure_ascii=False).encode('utf-8')
)

# ==================== 股票列表（20 只沪深 300 成分股） ====================
# 每个元素包含：股票代码（6位）、BaoStock 代码（带市场后缀）、名称
STOCK_LIST = [
    {"code": "000001", "baostock_code": "sz.000001", "name": "平安银行"},
    {"code": "600036", "baostock_code": "sh.600036", "name": "招商银行"},
    {"code": "000858", "baostock_code": "sz.000858", "name": "五粮液"},
    {"code": "601318", "baostock_code": "sh.601318", "name": "中国平安"},
    {"code": "002415", "baostock_code": "sz.002415", "name": "海康威视"},
    {"code": "000002", "baostock_code": "sz.000002", "name": "万科A"},
    {"code": "600519", "baostock_code": "sh.600519", "name": "贵州茅台"},
    {"code": "000333", "baostock_code": "sz.000333", "name": "美的集团"},
    {"code": "002594", "baostock_code": "sz.002594", "name": "比亚迪"},
    {"code": "300750", "baostock_code": "sz.300750", "name": "宁德时代"},
    {"code": "601166", "baostock_code": "sh.601166", "name": "兴业银行"},
    {"code": "600016", "baostock_code": "sh.600016", "name": "民生银行"},
    {"code": "000651", "baostock_code": "sz.000651", "name": "格力电器"},
    {"code": "002304", "baostock_code": "sz.002304", "name": "洋河股份"},
    {"code": "600030", "baostock_code": "sh.600030", "name": "中信证券"},
    {"code": "601888", "baostock_code": "sh.601888", "name": "中国中免"},
    {"code": "002352", "baostock_code": "sz.002352", "name": "顺丰控股"},
    {"code": "600309", "baostock_code": "sh.600309", "name": "万华化学"},
    {"code": "002475", "baostock_code": "sz.002475", "name": "立讯精密"},
    {"code": "600900", "baostock_code": "sh.600900", "name": "长江电力"},
]

# ==================== 历史数据采集（BaoStock） ====================
def fetch_historical_baostock(code, start_date='2025-06-01', end_date='2026-05-31'):
    """
    使用 BaoStock 获取单只股票的历史日线数据（前复权）
    参数：
        code: BaoStock 代码，如 'sh.600036'
        start_date: 起始日期 YYYY-MM-DD
        end_date: 结束日期 YYYY-MM-DD
    返回：
        DataFrame 包含 date, open, high, low, close, volume 列，失败返回 None
    """
    # 登录 BaoStock 系统
    lg = bs.login()
    if lg.error_code != '0':
        logger.error(f"BaoStock login failed: {lg.error_msg}")
        return None
    try:
        # 查询历史 K 线数据，adjustflag=3 表示前复权
        rs = bs.query_history_k_data_plus(
            code,
            "date,open,high,low,close,volume",
            start_date=start_date, end_date=end_date,
            frequency="d", adjustflag="3"
        )
        data_list = []
        # 迭代获取所有行
        while (rs.error_code == '0') and rs.next():
            data_list.append(rs.get_row_data())
        if data_list:
            # 转换为 DataFrame 并转换数据类型
            df = pd.DataFrame(data_list, columns=rs.fields)
            df['open'] = df['open'].astype(float)
            df['high'] = df['high'].astype(float)
            df['low'] = df['low'].astype(float)
            df['close'] = df['close'].astype(float)
            df['volume'] = df['volume'].astype(int)
            return df
        else:
            return None
    finally:
        # 无论成功与否，登出 BaoStock
        bs.logout()

# ==================== 实时行情采集（AkShare，带重试） ====================
@retry(stop=stop_after_attempt(5), wait=wait_exponential(multiplier=2, min=4, max=30))
def safe_fetch_spot():
    """获取全市场实时行情，如果失败自动重试（最多5次，等待时间指数增长）"""
    return ak.stock_zh_a_spot_em()

# ==================== 新闻采集（AkShare，带重试） ====================
@retry(stop=stop_after_attempt(5), wait=wait_exponential(multiplier=2, min=4, max=30))
def safe_fetch_news(code):
    """获取指定股票的新闻，自动重试"""
    return ak.stock_news_em(symbol=code)

# ==================== 采集历史日线并发送到 Kafka ====================
def fetch_historical():
    """遍历股票列表，调用 BaoStock 获取历史日线，每条记录作为消息发送到 Kafka topic 'stock-prices'"""
    start = '2025-06-01'
    end = '2026-05-31'
    for stock in STOCK_LIST:
        try:
            df = fetch_historical_baostock(stock['baostock_code'], start, end)
            if df is not None and not df.empty:
                logger.info(f"BaoStock fetched {len(df)} historical records for {stock['code']}")
                # 逐行构造 JSON 消息并发送
                for _, row in df.iterrows():
                    msg = {
                        'symbol': stock['code'],
                        'trade_time': row['date'] + ' 15:00:00',   # 统一收盘时间
                        'open': float(row['open']),
                        'high': float(row['high']),
                        'low': float(row['low']),
                        'close': float(row['close']),
                        'volume': int(row['volume']),
                        'data_type': 'daily'
                    }
                    producer.send('stock-prices', msg)
                producer.flush()   # 确保所有消息已发送
            else:
                logger.warning(f"No historical data for {stock['code']} from BaoStock")
            # 随机等待 1~2 秒，避免请求过快
            time.sleep(random.uniform(1, 2))
        except Exception as e:
            logger.error(f"Historical error for {stock['code']}: {e}")

# ==================== 采集新闻 ====================
def fetch_news(limit_per_stock=30):
    """
    采集每只股票的真实新闻，最多 limit_per_stock 条，发送到 Kafka topic 'stock-news'
    """
    for stock in STOCK_LIST:
        try:
            news_df = safe_fetch_news(stock['code'])
            if news_df is not None and not news_df.empty:
                sent = 0
                for _, row in news_df.head(limit_per_stock).iterrows():
                    news_msg = {
                        'symbol': stock['code'],
                        'title': row['新闻标题'],
                        'content': row.get('新闻内容', ''),
                        'publish_time': row['发布时间'],
                        'source': row.get('文章来源', 'eastmoney'),
                        'data_type': 'news'
                    }
                    producer.send('stock-news', news_msg)
                    sent += 1
                producer.flush()
                logger.info(f"Sent {sent} real news for {stock['code']}")
            else:
                logger.warning(f"No news for {stock['code']}, skip")
            # 随机等待 2~4 秒
            time.sleep(random.uniform(2, 4))
        except Exception as e:
            logger.error(f"News error for {stock['code']}: {e}")

# ==================== 实时行情采集（持续运行指定分钟数） ====================
def fetch_realtime_cycle(duration_minutes=300):
    """
    持续采集实时行情，每轮采集所有股票的最新价格，发送到 Kafka，持续 duration_minutes 分钟。
    每分钟采集一次（实际间隔随机 60~90 秒），以减轻服务器压力。
    """
    end_time = datetime.now() + timedelta(minutes=duration_minutes)
    count = 0
    while datetime.now() < end_time:
        try:
            # 获取实时行情表
            spot_df = safe_fetch_spot()
            for stock in STOCK_LIST:
                # 根据代码筛选对应行
                row = spot_df[spot_df['代码'] == stock['code']]
                if not row.empty:
                    msg = {
                        'symbol': stock['code'],
                        'trade_time': datetime.now().isoformat(),
                        'price': float(row['最新价'].values[0]),
                        'volume': int(row['成交量'].values[0]),
                        'turnover': float(row['成交额'].values[0]),
                        'data_type': 'realtime'
                    }
                    producer.send('stock-prices', msg)
                    count += 1
            producer.flush()
            logger.info(f"Sent {count} realtime records so far")
            # 随机等待 60~90 秒，避免固定频率被封锁
            time.sleep(random.uniform(60, 90))
        except Exception as e:
            logger.error(f"Realtime cycle error: {e}")
            time.sleep(random.uniform(30, 60))
    logger.info(f"Realtime collection finished, total records: {count}")

# ==================== 主程序 ====================
if __name__ == '__main__':
    logger.info("Starting data collector (BaoStock for history, AkShare for realtime/news)...")
    # 1. 采集历史日线数据
    fetch_historical()
    # 2. 采集新闻舆情
    fetch_news(limit_per_stock=30)
    # 3. 采集实时行情（持续300分钟，约5小时）
    logger.info("Starting realtime collection for 300 minutes...")
    fetch_realtime_cycle(duration_minutes=300)
    logger.info("Data collection completed.")