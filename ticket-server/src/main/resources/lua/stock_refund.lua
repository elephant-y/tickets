-- 库存回补脚本(扣减的镜像,下单失败/取消/退票时调用)
-- KEYS  = 所有重叠区间的库存 key
-- ARGV[1] = 回补票数
-- 返回: 1(加回去永远安全,不需要先验)

-- 注意:如果 key 已丢失(Redis 重启/过期),INCRBY 会把「回补数」当成初始值写入,
-- 造成错误数据 —— 该场景的兜底是 DB 层的 UPDATE 条件 + 阶段六的对账,本脚本保持简单。

for i = 1, #KEYS do
    redis.call("INCRBY", KEYS[i], tonumber(ARGV[1]))
end

return 1
