--[[
    库存查询 Lua 脚本
    
    功能：查询Redis中的可用库存
    
    KEYS[1] = inv:{warehouseId}:{skuId}
    
    返回值:
    nil: key不存在
    number: 可用库存数量
--]]

local invKey = KEYS[1]
return redis.call('GET', invKey)
