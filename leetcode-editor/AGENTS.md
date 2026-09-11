# LeetCode Module Guidelines

本文件适用于 `leetcode-editor/`。同时遵守仓库根目录 `AGENTS.md`。

## 范围与结构

- 本模块是 Java 8 算法题集合，代码位于 `src/main/java/com/linger/leetcode/editor/cn/`。
- 一道题通常对应一个独立类；复用现有 `ListNode`、`TreeNode` 等基础结构，不为单题复制同类模型。
- 修改题解时只处理目标题及其直接依赖，不批量格式化或重写无关题目。

## 实现约束

- 保持 Java 8 兼容，不使用更高版本语法或标准库 API。
- 类名使用 `PascalCase`，方法和变量使用 `camelCase`，缩进 4 空格。
- 优先给出时间和空间复杂度合理、边界明确的实现；避免仅为缩短代码牺牲可读性。
- 明确处理空输入、单元素、重复值、溢出和索引边界；不要依赖题目未保证的隐含条件。
- 保留题目平台需要的方法签名；除非需求明确，不改公开入口、包名或共享节点定义。

## 验证

从仓库根目录执行：

```powershell
mvn -pl leetcode-editor -am test
```

- 优先为修复的边界条件添加小而直接的 JUnit 5 测试；若模块当前没有对应测试结构，只做目标类的代码级核对并在交付时说明。
- 除非用户明确要求，不运行整个 Maven Reactor 的全量构建。
