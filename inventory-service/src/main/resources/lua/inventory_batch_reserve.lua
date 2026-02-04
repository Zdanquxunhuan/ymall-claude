--[[
    批量库存预留 Lua 脚本
    
    功能：原子性检查并扣减多个SKU的可用库存（用于一个订单包含多个商品的场景）
    
    KEYS: 
    - KEYS[1..n] = inv:{warehouseId}:{skuId} (库存key列表)
    - KEYS[n+1..2n] = inv:reserved:{orderNo}:{warehouseId}:{skuId} (幂等标记列表)
    
    ARGV:
    - ARGV[1] = SKU数量 n
    - ARGV[2..n+1] = 每个SKU的预留数量
    - ARGV[n+2] = 幂等过期时间秒数
    
    返回值:
    -1: 库存不足（返回格式: "-1:{skuIndex}"，skuIndex从0开始）
    -2: 库存key不存在（返回格式: "-2:{skuIndex}"）
    0: 已经预留过（幂等返回）
    1: 预留成功
--]]

local skuCount = tonumber(ARGV[1])
local expireSeconds = tonumber(ARGV[skuCount + 2]) or 86400

-- 第一步：检查是否已经预留过（任意一个SKU已预留则认为整个订单已预留）
for i = 1, skuCount do
    local reservedKey = KEYS[skuCount + i]
    if redis.call('EXISTS', reservedKey) == 1 then
        return "0"
    end
end

-- 第二步：检查所有SKU库存是否充足
local stockList = {}
for i = 1, skuCount do
    local invKey = KEYS[i]
    local currentStock = redis.call('GET', invKey)
    
    if not currentStock then
        return "-2:" .. (i - 1)
    end
    
    local available = tonumber(currentStock)
    local qty = tonumber(ARGV[i + 1])
    
    if available < qty then
        return "-1:" .. (i - 1)
    end
    
    stockList[i] = {key = invKey, available = available, qty = qty}
end

-- 第三步：扣减所有SKU库存并设置幂等标记
for i = 1, skuCount do
    local item = stockList[i]
    local newAvailable = item.available - item.qty
    redis.call('SET', item.key, newAvailable)
    
    local reservedKey = KEYS[skuCount + i]
    redis.call('SETEX', reservedKey, expireSeconds, item.qty)
end

return "1"
