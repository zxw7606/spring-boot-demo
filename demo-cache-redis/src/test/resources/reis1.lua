
local key = KEYS[1]
-- 没秒最大请求数
local max_burst_seconds = ARGV[1]
-- 存储的最大数量
local max_permits = ARGV[2]
-- 每个请求需要的毫秒
--local stable_interval_millis =  1 * 1000 / max_burst_seconds
local stable_interval_millis =  ARGV[3]
-- 存储的令牌
local stored_permits = ARGV[4]
-- 获取时毫秒级间戳
local now_millis= ARGV[5]
-- 下一次刷新Token的时间
local next_free_ticket_millis = now_millis
-- 创建一个Redis限流器
if (redis.call('exists', key) == 0) then
  --max_burst_seconds
  redis.call('hset', key, "max_burst_seconds" , max_burst_seconds);
  --max_permits
  redis.call('hset', key, "max_permits", max_permits);
  --stable_interval_millis
  redis.call('hset', key, "stable_interval_millis", stable_interval_millis);
  --stored_permits
  redis.call('hset', key, "stored_permits", stored_permits);
  --next_free_ticket_millis
  redis.call('hset', key, "next_free_ticket_millis", next_free_ticket_millis);

  return next_free_ticket_millis
  --过期时间
  --redis.call('pexpire', key, ARGV[6]);
else
  local smooth_bursty_rate_limiter = redis.call('hget', key, 'next_free_ticket_millis')
   return smooth_bursty_rate_limiter
end

-- next_free_ticket_millis
if (now_millis > next_free_ticket_millis) then
  local new_permits = (now_millis - next_free_ticket_millis) / stable_interval_millis
  stored_permits = math.min(max_permits, stored_permits + new_permits);
  next_free_ticket_millis = now_millis;
end


-- 所需要的令牌
local permits = 1

-- 刷新令牌
if now_millis > next_free_ticket_millis then
  local new_permits = (now_millis - next_free_ticket_millis) / stable_interval_millis
  stored_permits = math.min(max_permits, stored_permits + new_permits);
  next_free_ticket_millis = now_millis;
end

-- 刷新令牌的数量过后在判断获取N个令牌需要等待多少时间

local stored_permits_to_spend = math.min(permits, stored_permits);

local fresh_permits = permits - stored_permits_to_spend;

-- 代码参考 SmoothRateLimiter , 这里 SmoothRateLimiter 里面把wait时间放到下一次的请求里面, 这里直接返回比较符合场景
local wait_millis = fresh_permits * stable_interval_millis;

stored_permits = stored_permits - stored_permits_to_spend

-- save

redis.pcall("HMSET",key,"max_burst_seconds", max_burst_seconds)
redis.pcall("HMSET",key,"max_permits", max_permits)
redis.pcall("HMSET",key,"stable_interval_millis", stable_interval_millis)
redis.pcall("HMSET",key,"stored_permits", stored_permits)
redis.pcall("HMSET",key,"next_free_ticket_millis", next_free_ticket_millis)


return wait_millis;
