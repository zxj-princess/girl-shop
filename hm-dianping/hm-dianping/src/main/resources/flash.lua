-- 捡漏下单 Lua 脚本：原子性完成「扣库存 + 一人一单校验」
-- 返回：0 成功，1 库存不足，2 重复下单
local productId = ARGV[1]
local userId = ARGV[2]
local stockKey = 'flash:stock:' .. productId
local orderKey = 'flash:order:' .. productId

-- 判断库存是否充足
if (tonumber(redis.call('get', stockKey) or '0') <= 0) then
    return 1
end
-- 判断是否重复下单
if (redis.call('sismember', orderKey, userId) == 1) then
    return 2
end
-- 扣库存
redis.call('incrby', stockKey, -1)
-- 标记已下单
redis.call('sadd', orderKey, userId)
return 0