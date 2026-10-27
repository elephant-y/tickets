-- 库存扣减脚本(区间联动,阶段四核心)
-- KEYS  = 所有重叠区间的库存 key(Java 侧算好传入,Lua 不做业务判断)
-- ARGV[1] = 扣减票数
-- 返回: 1 成功 | -1 存在未初始化的 key(Java 侧懒加载后重试)| -2 余票不足

-- ===== 第一遍:全部检查,任一个不足 → 整体失败,分文不扣 =====
-- 为什么必须两遍:Lua 有原子性但没有回滚——边验边扣的话,
-- 扣到一半发现不足,已扣的部分退不回去。先验后扣 = 用遍历模拟事务。
for i = 1, #KEYS do
    local remain = redis.call("GET", KEYS[i])
    if remain == false then
        return -1
    end
    if tonumber(remain) < tonumber(ARGV[1]) then
        return -2
    end
end

-- ===== 第二遍:全部通过,才开始扣 =====
for i = 1, #KEYS do
    redis.call("DECRBY", KEYS[i], tonumber(ARGV[1]))
end

return 1
