--[[
    库存释放 Lua 脚本
    
    功能：原子性归还预留的库存到可用库存
    
    Key: inv:{warehouseId}:{skuId}
    Value: 可用库存数量 (available_qty)
    
    KEYS[1] = inv:{warehouseId}:{skuId}
    KEYS[2] = inv:reserved:{orderNo}:{warehouseId}:{skuId} (幂等标记)
    
    ARGV[1] = 释放数量 (qty)
    
    返回值:
    -1: 幂等标记不存在（可能已释放或未预留）
    -2: 库存key不存在
    >0: 释放成功，返回归还后的可用库存
--]]

-- 检查幂等标记是否存在
local reservedKey = KEYS[2]
local reservedQty = redis.call('GET', reservedKey)

if not reservedQty then
    -- 幂等标记不存在，可能已释放或未预留
    return -1
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

-- 归还库存
local newAvailable = available + qty
redis.call('SET', invKey, newAvailable)

-- 删除幂等标记
redis.call('DEL', reservedKey)

return newAvailable
