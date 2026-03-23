-- ============================================================
-- rate_limit.lua — Redis + Lua 令牌桶限流脚本
-- ============================================================
-- KEYS[1] = 限流 key (e.g. rate_limit:{userId}:{endpoint})
-- ARGV[1] = 桶容量 (max_tokens)
-- ARGV[2] = 填充速率 (tokens_per_second)
-- ARGV[3] = 当前时间戳 (毫秒)
-- ARGV[4] = 请求消耗的 token 数 (通常为 1)
--
-- 返回: {allowed(0/1), remaining_tokens}
-- ============================================================

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local now_ms = tonumber(ARGV[3])
local cost = tonumber(ARGV[4])

if capacity <= 0 or refill_rate <= 0 or cost <= 0 then
	return {0, 0, 1}
end

local data = redis.call('HMGET', key, 'tokens', 'ts')
local last_tokens = tonumber(data[1])
local last_ts = tonumber(data[2])

if last_tokens == nil then
	last_tokens = capacity
	last_ts = now_ms
end

local elapsed_ms = math.max(0, now_ms - last_ts)
local refill = (elapsed_ms / 1000.0) * refill_rate
local current_tokens = math.min(capacity, last_tokens + refill)

local allowed = 0
if current_tokens >= cost then
	allowed = 1
	current_tokens = current_tokens - cost
end

redis.call('HMSET', key, 'tokens', current_tokens, 'ts', now_ms)

local ttl_seconds = math.ceil((capacity / refill_rate) * 2)
if ttl_seconds < 1 then
	ttl_seconds = 1
end
redis.call('EXPIRE', key, ttl_seconds)

local remaining = math.floor(current_tokens)
local reset_in = math.ceil((capacity - current_tokens) / refill_rate)
if reset_in < 1 then
	reset_in = 1
end

return {allowed, remaining, reset_in}
