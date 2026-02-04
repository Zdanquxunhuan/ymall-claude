--[[
    批量库存释放 Lua 脚本
    
    功能：原子性归还多个SKU的预留库存
    
    KEYS: 
    - KEYS[1..n] = inv:{warehouseId}:{skuId} (库存key列表)
    - KEYS[n+1..2n] = inv:reserved:{orderNo}:{warehouseId}:{skuId} (幂等标记列表)
    
    ARGV:
    - ARGV[1] = SKU数量 n
    - ARGV[2..n+1] = 每个SKU的释放数量
    
    返回值:
    0: 幂等标记不存在（可能已释放或未预留）
    1: 释放成功
--]]

local skuCount = tonumber(ARGV[1])

-- 检查是否有任何幂等标记存在
local hasReserved = false
for i = 1, skuCount do
    local reservedKey = KEYS[skuCount + i]
    if redis.call('EXISTS', reservedKey) == 1 then
        hasReserved = true
        break
    end
end

if not hasReserved then
    -- 没有任何幂等标记，可能已释放
    return "0"
end

-- 释放所有SKU库存
for i = 1, skuCount do
    local reservedKey = KEYS[skuCount + i]
    local reservedQty = redis.call('GET', reservedKey)
    
    if reservedQty then
        local invKey = KEYS[i]
        local currentStock = redis.call('GET', invKey)
        
        if currentStock then
            local available = tonumber(currentStock)
            local qty = tonumber(ARGV[i + 1])
            local newAvailable = available + qty
            redis.call('SET', invKey, newAvailable)
        end
        
        redis.call('DEL', reservedKey)
    end
end

return "1"
