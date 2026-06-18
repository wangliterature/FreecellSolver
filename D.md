## 一年的开始
不同朝代会规定一年从哪一个月开始，它不仅是历法问题，也是政治象征。

1.夏朝（夏历）：以建寅（农历正月）为岁首，相当于现在农历正月，一直影响到后世
2.商朝（殷历）：以建丑（农历十二月）为岁首
3.周朝（周历）：以建子（农历十一月）为岁首
4.秦朝：沿用周制，也是十一月为正月
5.汉武帝以后（太初历）：重新回到建寅（正月）为岁首，一直沿用到今天的农历

## 闰月是怎么来的

中国古代用的是阴阳合历,月亮周期（朔望月）≈ 29.5天,一年12个月 ≈ 354天,但太阳年 ≈ 365天,每年差 约11天
如果不出现闰月，春节会越过四季

规则核心是：当某一年**没有“中气”**的月份，就设为闰月

### 方法如下

二十四节气中分两类：节气（立春、惊蛰…）    中气（雨水、春分…）

一个农历月必须有一个“中气”才算正常月，没有中气 → 这个月就是闰月

二十四节气，一般算命里面，会分为节和气，一般的算几岁起运，就是出生时候到下一个节的天数，初一3

## 二十四节气

核心是：把太阳在黄道上的位置分成24份，每15°一个节气，用来指导农业。

🧩 moveModeNames 含义
static private String[] moveModeNames = new String[]{
"?",              // 0 未知 / 未定义
"toAces",         // 1 移到基础堆（A → K）
"fromSpace",      // 2 从空闲单元拿牌
"toSpace",        // 3 放到空闲单元
"fromWork",       // 4 从工作列（主牌列）拿牌
"toWork",         // 5 移到工作列
"matching",       // 6 匹配（通常是自动拼接序列）
"toAcesAuto",     // 7 自动移到基础堆（自动收牌）
"expose",         // 8 翻开新牌（暴露底牌）
"matchWithSplit", // 9 拆分后匹配（复杂移动）
"toSpaceKing"     // 10 King 放入空列（特殊规则）
};
🎯 重点解释几个关键操作
1️⃣ toAces
把牌移动到右上角的基础堆（A → K）
FreeCell 的核心目标
2️⃣ toSpace / fromSpace
空闲单元（FreeCell 的“4个临时格”）
用来中转牌
3️⃣ toWork / fromWork
主游戏区域（8列）
规则：
颜色交替（红黑）
数值递减（K → A）
4️⃣ toAcesAuto
自动收牌（常见功能）
比如：
当某张牌已经“安全”时自动上收
5️⃣ matching
多张牌连续移动（比如一串顺序正确的牌）
常用于：
自动拼接
AI 或提示系统
6️⃣ matchWithSplit
比较高级的逻辑：
先拆开一段序列
再重新组合移动
一般出现在：
AI 搜索
高级解法
7️⃣ expose
翻开被盖住的牌（类似“揭底牌”）
比如移动后露出新牌
8️⃣ toSpaceKing
特殊规则：
King（K）移动到空列
很多 FreeCell 实现会单独处理这个情况


move

Solved FreeCell in 67 moves
0.	 move 0   1 card: Tableau[0] -> Foundation[3] [10023]
1.	 move 0   1 card: Tableau[5] -> FreeCell[0] [10510]
2.	 move 2   1 card: Tableau[5] -> Tableau[3] [2010503]
3.	 move 2   1 card: Tableau[5] -> Tableau[1] [2010501]
4.	 move 2   1 card: Tableau[0] -> Tableau[5] [2010005]
5.	 move 16   1 card: Tableau[0] -> Foundation[0] (auto) [16010020]
6.	 move 2   2 cards: Tableau[5] -> Tableau[4] [2020504]
7.	 move 2   2 cards: Tableau[1] -> Tableau[5] [2020105]
8.	 move 16   1 card: Tableau[1] -> Foundation[2] (auto) [16010122]
9.	 move 2   1 card: FreeCell[0] -> Tableau[1] [2011001]
10.	 move 2   1 card: Tableau[0] -> Tableau[1] [2010001]
11.	 move 2   3 cards: Tableau[1] -> Tableau[0] [2030100]
12.	 move 2   1 card: Tableau[1] -> Tableau[3] [2010103]
13.	 move 2   4 cards: Tableau[0] -> Tableau[1] [2040001]
14.	 move 2   3 cards: Tableau[5] -> Tableau[1] [2030501]
15.	 move 2   1 card: Tableau[5] -> Tableau[4] [2010504]
16.	 move 0   1 card: Tableau[0] -> Tableau[5] [10005]
17.	 move 1   1 card: Tableau[1] -> FreeCell[0] (split) [1010110]
18.	 move 2   1 card: Tableau[5] -> Tableau[1] [2010501]
19.	 move 2   8 cards: Tableau[1] -> Tableau[0] [2080100]
20.	 move 2   2 cards: Tableau[1] -> Tableau[6] [2020106]
21.	 move 0   3 cards: Tableau[6] -> Tableau[1] [30601]
22.	 move 0   4 cards: Tableau[4] -> Tableau[5] [40405]
23.	 move 2   1 card: Tableau[4] -> Tableau[0] [2010400]
24.	 move 2   4 cards: Tableau[5] -> Tableau[4] [2040504]
25.	 move 2   5 cards: Tableau[4] -> Tableau[7] [2050407]
26.	 move 2   1 card: Tableau[4] -> Tableau[3] [2010403]
27.	 move 2   4 cards: Tableau[3] -> Tableau[4] [2040304]
28.	 move 2   6 cards: Tableau[7] -> Tableau[4] [2060704]
29.	 move 18   1 card: Tableau[7] -> Foundation[0] (auto) [18010720]
30.	 move 0   1 card: Tableau[6] -> Tableau[5] [10605]
31.	 move 16   1 card: Tableau[6] -> Foundation[1] (auto) [16010621]
32.	 move 19   1 card: Tableau[4] -> Foundation[1] (split) (auto) [19010421]
33.	 move 3   1 card: Tableau[4] -> Foundation[0] (split) [3010420]
34.	 move 2   1 card: FreeCell[0] -> Foundation[0] [2011020]
35.	 move 2   1 card: Tableau[3] -> Tableau[4] [2010304]
36.	 move 18   1 card: Tableau[3] -> Foundation[3] (auto) [18010323]
37.	 move 3   1 card: Tableau[0] -> Foundation[3] (split) [3010023]
38.	 move 2   1 card: Tableau[3] -> Tableau[7] [2010307]
39.	 move 2   1 card: Tableau[6] -> Tableau[3] [2010603]
40.	 move 18   1 card: Tableau[6] -> Foundation[2] (auto) [18010622]
41.	 move 19   1 card: Tableau[4] -> Foundation[2] (split) (auto) [19010422]
42.	 move 19   1 card: Tableau[4] -> Foundation[3] (split) (auto) [19010423]
43.	 move 0   1 card: Tableau[2] -> FreeCell[0] [10210]
44.	 move 3   1 card: Tableau[4] -> Foundation[0] (split) [3010420]
45.	 move 3   1 card: Tableau[0] -> Foundation[2] (split) [3010022]
46.	 move 19   1 card: Tableau[0] -> Foundation[3] (split) (auto) [19010023]
47.	 move 3   1 card: Tableau[3] -> Foundation[2] (split) [3010322]
48.	 move 19   1 card: Tableau[4] -> Foundation[3] (split) (auto) [19010423]
49.	 move 3   1 card: Tableau[0] -> Foundation[2] (split) [3010022]
50.	 move 3   1 card: Tableau[4] -> Foundation[2] (split) [3010422]
51.	 move 3   1 card: Tableau[0] -> Foundation[3] (split) [3010023]
52.	 move 3   1 card: Tableau[0] -> Foundation[2] (split) [3010022]
53.	 move 2   1 card: Tableau[2] -> Tableau[4] [2010204]
54.	 move 2   1 card: Tableau[5] -> Tableau[2] [2010502]
55.	 move 2   2 cards: Tableau[2] -> Tableau[1] [2020201]
56.	 move 2   4 cards: Tableau[0] -> Tableau[2] [2040002]
57.	 move 2   1 card: Tableau[3] -> Tableau[4] [2010304]
58.	 move 2   7 cards: Tableau[4] -> Tableau[6] [2070406]
59.	 move 2   2 cards: Tableau[7] -> Tableau[2] [2020702]
60.	 move 0   7 cards: Tableau[2] -> Tableau[0] [70200]
61.	 move 2   1 card: Tableau[2] -> Tableau[1] [2010201]
62.	 move 2   1 card: FreeCell[0] -> Tableau[2] [2011002]
63.	 move 2   2 cards: Tableau[2] -> Tableau[7] [2020207]
64.	 move 2   3 cards: Tableau[7] -> Tableau[2] [2030702]
65.	 move 2   1 card: Tableau[7] -> Tableau[2] [2010702]
66.	 move 2   1 card: Tableau[3] -> Foundation[0] [2010320]

