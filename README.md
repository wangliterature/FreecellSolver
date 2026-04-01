# FreeCell 独立版

目前Freeceell部分已经全部拆完。下来开始学习了，看懂。

## 说明

将group分为了三块，桌面部分[0] + 活动区[1] + 收牌区[2]


## 执行过程

### 准备工作

- 获取关卡路径
- 检测数据是否正确
- 创建context
- 创建解题器

### 解题开始

#### 第一部分 准备

- 分配栈的大小
- 读取关卡
- 解析
- 创建topRun 【在stack中不保存但张牌的，保存的都是CardRun】
- 复制当前state
- 最大搜索步数298
- 信誉searchCreditLimit
- 创建缓存空间 

#### 第二部分  开始搜索

- 记录search次数
- 评估布局
    - 查看当前状态，是不是有解了
    - 当前深度和最好的比较  
    - 打分
- 对于深度太深的直接删除了
- 是否将目前已知的最优解却认为最优解

- 评估结束了
- 在深度为0时，求解器会立即有机会直接移动到基础步骤
- 找候选移动


### 方法解析

- tryDirectFoundationMoves

```txt
遍历所有的Foundation Group部分

从Table和Free区域放入foundation，一旦找到直接返回。
```

- tryFoundationMovesFromSources 针对一个 foundation 目标，依次尝试一组常规来源。

```txt
1.遍历每一列的table
```

- tryMoveStackAndRecurse

* 这个方法的责任只有四步：
* 1. 过滤明显不可能或不值得试的组合；
* 2. 评估这次 join 理论上能搬多少张；
* 3. 检查 FreeCell 对空列/空闲单元的搬运上限；
* 4. 真正执行移动、递归搜索，然后把现场恢复。


## 类说明

### StackGroup

栈group，里面存放的是栈，Freecell游戏包含三个区域：牌区    自由区   收牌区

### CardStack

牌栈中存放CardRun，每一个栈分为多个CardRun保存

### Card

牌的id,以及牌的花色和值

### CardRun

包含几个card

