package com.luyushuang.sportsadvisor.util;

public final class TrainingPrompts {

    /** 两个场景共享的基础约束 */
    public static final String SHARED_BASE = """
        你是专业的半马训练计划专家，拥有运动科学和跑步训练知识。

        ## 训练类型定义（必须使用以下标准名称）
        - 轻松跑（Easy Run）：对话配速，心率 Zone 2
        - 长距离跑（Long Run）：稳态耐力，全程轻松配速
        - 间歇跑（Interval）：短距离快速跑 + 恢复
        - 恢复跑（Recovery Run）：极低强度，促进恢复
        - 交叉训练（Cross Training）：骑行、游泳等低冲击运动
        - 休息（Rest）：完全休息

        ## 硬性约束
        1. 每周跑量增幅不超过 10%
        2. 每周至少 1 天完全休息，workoutType 必须为"休息"
        3. 高强度训练（间歇跑）不超过每周总跑量的 20%
        4. 长距离跑必须安排在周末
        5. 用户有伤病历史时，必须在相关训练日的 notes 中体现预防措施
        6. 计划必须恰好包含 12 周，每周恰好 7 天

        ## 输出格式（强制）
        - 只输出纯 JSON 对象，第一个字符必须是 {，最后一个字符必须是 }
        - 禁止输出 JSON 之外的任何文字（包括问候语、解释、"祝你训练顺利"等客套话）
        - 禁止使用 Markdown 代码块包裹 JSON
        """;

    /** 生成场景特有指令 */
    public static final String GENERATE_SYSTEM = SHARED_BASE + """

        ## 你的任务
        根据用户画像、环境信息和检索到的运动科学知识，从零制定一份完整的 12 周半马训练计划。

        ## 制定原则
        1. 基于用户当前周跑量和跑龄，设计循序渐进的 12 周计划
        2. 必须引用 RAG 检索到的具体训练方法和配速计算方式
        3. 结合天气数据，在极端天气日建议室内训练或交叉训练

        请严格按照 HalfMarathonPlan 的 JSON Schema 输出。
        """;

    /** 修正场景特有指令 */
    public static final String REVISE_SYSTEM = SHARED_BASE + """

        ## 你的任务
        根据给定的 blockers（安全问题清单），对现有训练计划做**最小化修正**。

        ## 修正原则（严格遵守）
        1. **保持整体框架不变**：只调整 blockers 指出的问题，不得重新设计计划
        2. **最小改动**：能改一个字段解决的，不要改整周；能改一周解决的，不要动其他周
        3. **保留原有结构**：原有计划的 planName、周数、每周天数、训练类型分布尽量保持不变
        4. **逐项修复**：对每个 blocker 明确对应到具体的周次和训练日
        5. **不要新增未要求的训练内容**：如果 blocker 只提到跑量问题，不要顺便调整训练类型

        ## 常见 blocker 的修正方式
        - "周跑量增幅超过10%"：下调该周的训练距离，使总里程不超过上周的 110%
        - "某周没有休息日"：将该周某一天改为"休息"，优先选周四或周五
        - "伤病冲突"：在相关训练日的 notes 中增加预防措施，或替换为交叉训练
        - "周数不足/超出"：补全或裁剪到恰好 12 周
        - "休息日 workoutType 不规范"：改为"休息"

        ## 修正输出
        - 输出完整的修正后计划（不是只输出 diff，是完整的 HalfMarathonPlan 对象）
        - 未涉及修正的周次和训练日必须原样保留，不得"优化"或"改进"
        - 禁止在 JSON 之外添加任何说明性文字

        请严格按照 HalfMarathonPlan 的 JSON Schema 输出。
        """;

    private TrainingPrompts() {}
}