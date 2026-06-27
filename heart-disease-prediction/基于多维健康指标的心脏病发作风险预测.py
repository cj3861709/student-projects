# 基于多维健康指标的心脏病发作风险预测
# 运行环境：Anaconda + Python
# 核心算法：逻辑回归、K近邻、决策树、随机森林
# 优化方向：类别权重平衡、下调分类阈值(降低医疗场景漏诊)
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import warnings
warnings.filterwarnings('ignore')  # 屏蔽无关警告，避免干扰运行结果

# ========== 解决 Matplotlib 中文乱码（Anaconda 环境专用配置） ==========
plt.rcParams['font.sans-serif'] = ['SimHei']   # 设置字体为黑体，正常显示中文
plt.rcParams['axes.unicode_minus'] = False      # 解决坐标轴负号显示异常问题

# ===================== 1. 数据加载与预处理 =====================
# 读取本地数据集 heart.csv（心脏病患者多维健康指标数据）
df = pd.read_csv("heart.csv")

# 输出数据集基础信息：字段类型、非空值数量
print("===== 数据集基本信息 =====")
print(df.info())

# 输出描述性统计：均值、最值、分位数等，查看数据分布
print("\n===== 描述性统计 =====")
print(df.describe())

# 输出标签分布：0=健康，1=患病，验证样本均衡性
print("\n===== 标签分布(0=健康 1=患病) =====")
print(df['target'].value_counts())

# 划分特征集X与目标标签y
# X：13项生理指标（年龄、血压、心率等），用于模型输入
# y：预测目标（是否患心脏病，二分类任务）
X = df.drop('target', axis=1)
y = df['target']

# 数据标准化处理
# 原因：血压、胆固醇等指标量纲差异大，会影响线性模型、距离类模型效果
from sklearn.preprocessing import StandardScaler
scaler = StandardScaler()
X_scaled = scaler.fit_transform(X)

# 划分训练集与测试集：训练集70%，测试集30%
# random_state=42：固定随机种子，保证实验结果可复现
# stratify=y：分层抽样，保证训练/测试集正负样本比例和原数据一致
from sklearn.model_selection import train_test_split
X_train, X_test, y_train, y_test = train_test_split(
    X_scaled, y, test_size=0.3, random_state=42, stratify=y
)

# ===================== 2. 数据可视化 =====================
# 2.1 标签分布柱状图：直观查看健康/患病样本数量
plt.figure(figsize=(8,5))
df['target'].value_counts().plot(kind='bar', color=['skyblue','salmon'])
plt.title('心脏病患病标签分布')
plt.xlabel('0=健康  1=患病')
plt.ylabel('样本数量')
plt.tight_layout()
plt.show()

# 2.2 年龄分布直方图：分析心脏病高发年龄段
plt.figure(figsize=(8,5))
plt.hist(df['age'], bins=15, color='lightgreen', edgecolor='black')
plt.title('患者年龄分布')
plt.xlabel('年龄')
plt.ylabel('人数')
plt.tight_layout()
plt.show()

# 2.3 特征相关性热力图：挖掘指标与患病标签的关联程度
import seaborn as sns
plt.figure(figsize=(12,10))
corr = df.corr()
sns.heatmap(corr, cmap='coolwarm', annot=False)
plt.title('特征相关性热力图')
plt.tight_layout()
plt.show()

# ===================== 3. 模型定义与评估函数（核心算法部分） =====================
# 导入四类经典二分类机器学习算法
from sklearn.linear_model import LogisticRegression        # 逻辑回归（线性分类模型）
from sklearn.neighbors import KNeighborsClassifier       # K近邻（距离类惰性模型）
from sklearn.tree import DecisionTreeClassifier           # 决策树（树形非线性模型）
from sklearn.ensemble import RandomForestClassifier      # 随机森林（集成学习模型）
# 修复导入：正确AUC函数roc_auc_score
from sklearn.metrics import confusion_matrix, classification_report, roc_auc_score

"""
模型统一优化策略（适配医疗场景）：
1. class_weight='balanced'：类别权重平衡，加大患病样本学习权重，减少漏诊
2. random_state=42：固定随机状态，保证模型结果可复现
"""
models = {
    # ========== 算法1：逻辑回归 ==========
    # 原理：线性计算 + Sigmoid函数，输出0~1患病概率，属于经典二分类线性模型
    # max_iter=1000：增大最大迭代次数，解决高维数据下模型不收敛问题
    "逻辑回归": LogisticRegression(max_iter=1000, class_weight='balanced', random_state=42),

    # ========== 算法2：K近邻(KNN) ==========
    # 原理：计算样本间距离，取最近K个样本投票分类，惰性模型（无显式训练过程）
    # n_neighbors=5：K值设为5，选取距离最近的5个样本作为参考
    "K近邻": KNeighborsClassifier(n_neighbors=5),

    # ========== 算法3：决策树 ==========
    # 原理：模拟人工判断，基于特征逐层分支生成决策规则，可解释性强
    # max_depth=6：限制树最大深度，防止模型过拟合（过度学习训练集噪声）
    "决策树": DecisionTreeClassifier(max_depth=6, class_weight='balanced', random_state=42),

    # ========== 算法4：随机森林 ==========
    # 原理：集成学习，由多棵独立决策树投票输出结果，规避单决策树过拟合问题
    # n_estimators=100：集成100棵决策树构成模型整体
    # max_depth=6：限制单棵树深度，统一规则、防止过拟合
    "随机森林": RandomForestClassifier(n_estimators=100, max_depth=6, class_weight='balanced', random_state=42)
}

"""
自定义模型评估函数
参数顺序已修正：model, X_train, y_train, X_test, y_test, threshold
核心优化：threshold=0.4 分类阈值下调，适配医疗宁误诊不漏诊需求
"""
def model_evaluate(model, X_train, y_train, X_test, y_test, threshold=0.4):
    # 模型训练：使用训练集特征+标签学习数据规律
    model.fit(X_train, y_train)
    
    # predict_proba：输出每一类的概率，[:,1] 提取患病概率
    y_pred_proba = model.predict_proba(X_test)
    # 基于自定义阈值生成预测标签
    y_pred = (y_pred_proba[:,1] >= threshold).astype(int)

    # ========== 混淆矩阵讲解（答辩核心） ==========
    # TN(真负)：真实健康，预测健康
    # FP(假正)：真实健康，预测患病 → 误诊
    # FN(假负)：真实患病，预测健康 → 漏诊（医疗场景首要规避风险）
    # TP(真正)：真实患病，预测患病
    cm = confusion_matrix(y_test, y_pred)
    
    # 分类报告：输出精确率、召回率、F1值
    report = classification_report(y_test, y_pred, output_dict=True)
    # 计算AUC评估整体区分能力
    auc = roc_auc_score(y_test, y_pred_proba[:,1])

    # 控制台打印结果
    print("="*50)
    print(f"模型：{model.__class__.__name__}  分类阈值={threshold}")
    print("混淆矩阵：")
    print(cm)
    print("分类报告：")
    print(classification_report(y_test, y_pred))
    print(f"AUC值：{auc:.4f}")

    # 绘制混淆矩阵热力图【修复标题冗长bug】
    plt.figure(figsize=(6,5))
    sns.heatmap(cm, annot=True, fmt="d", cmap="Blues")
    plt.title(f"{model.__class__.__name__} 混淆矩阵") # 只取简洁模型名称
    plt.ylabel("真实标签")
    plt.xlabel("预测标签")
    plt.tight_layout()
    plt.show()
    
    return report, auc

# ===================== 4. 模型训练、评估、结果汇总 =====================
result_list = []
# 正确匹配入参顺序，无样本数量不匹配报错
for name, model in models.items():
    report, auc = model_evaluate(model, X_train, y_train, X_test, y_test, threshold=0.4)
    # 提取患病类别(1)三大核心指标
    precision = report['1']['precision']
    recall = report['1']['recall']
    f1 = report['1']['f1-score']
    result_list.append([name, precision, recall, f1, auc])

# 生成所有模型指标对比表格
result_df = pd.DataFrame(result_list, columns=["模型","精确率","召回率","F1值","AUC"])
print("\n===== 所有模型指标汇总 =====")
print(result_df)

# 随机森林特征重要性可视化，用于临床特征解读
rf = models["随机森林"]
feature_importance = pd.Series(rf.feature_importances_, index=df.columns[:-1])
plt.figure(figsize=(10,6))
feature_importance.sort_values(ascending=False).plot(kind='bar')
plt.title("随机森林-特征重要性排序")
plt.ylabel("重要性分值")
plt.tight_layout()
plt.show()