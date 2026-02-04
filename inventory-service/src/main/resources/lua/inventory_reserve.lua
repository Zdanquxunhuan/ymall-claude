--[[
    库存预留 Lua 脚本
    
    功能：原子性检查并扣减可用库存
    
    Key: inv:{warehouseId}:{skuId}
    Value: 可用库存数量 (available_qty)
    
    KEYS[1] = inv:{warehouseId}:{skuId}
    KEYS[2] = inv:reserved:{orderNo}:{warehouseId}:{skuId} (幂等标记)
    
    ARGV[1] = 预留数量 (qty)
    ARGV[2] = 幂等过期时间秒数 (默认86400秒=24小时)
    
    返回值:
    -1: 库存不足
    -2: 库存key不存在
    0: 已经预留过（幂等返回）
    >0: 预留成功，返回扣减后的可用库存
--]]

-- 检查是否已经预留过（幂等）
local reservedKey = KEYS[2]
local alreadyReserved = redis.call('EXISTS', reservedKey)
if alreadyReserved == 1 then
    -- 已经预留过，幂等返回
    return 0
end

-- 获取当前可用库存
local invKey = KEYS[1]
local currentStock = redis.call('GET', invKey)

-- 库存key不存在
if not currentStock then
    return -2
end

local available = tonumber(currentStock)
local qty = tonumber(ARGV[1])
local expireSeconds = tonumber(ARGV[2]) or 86400

-- 检查库存是否充足
if available < qty then
    return -1
end

-- 扣减库存
local newAvailable = available - qty
redis.call('SET', invKey, newAvailable)

-- 设置幂等标记（带过期时间）
redis.call('SETEX', reservedKey, expireSeconds, qty)

return newAvailable
