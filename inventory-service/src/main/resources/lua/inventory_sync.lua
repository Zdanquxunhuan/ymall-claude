--[[
    库存初始化/同步 Lua 脚本
    
    功能：将数据库中的库存同步到Redis
    
    KEYS[1] = inv:{warehouseId}:{skuId}
    
    ARGV[1] = 可用库存数量
    
    返回值:
    1: 设置成功
--]]

local invKey = KEYS[1]
local available = tonumber(ARGV[1])

redis.call('SET', invKey, available)

return 1
