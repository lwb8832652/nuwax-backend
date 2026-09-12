package com.xspaceagi.system.application.service.impl;

import com.xspaceagi.system.application.dto.TenantConfigDto;
import com.xspaceagi.system.application.dto.UserDto;
import com.xspaceagi.system.application.service.AuthService;
import com.xspaceagi.system.application.service.UserApplicationService;
import com.xspaceagi.system.infra.dao.entity.User;
import com.xspaceagi.system.infra.rpc.WeChatMpService;
import com.xspaceagi.system.infra.verify.VerifyCodeSendAndCheckService;
import com.xspaceagi.system.spec.common.RequestContext;
import com.xspaceagi.system.spec.enums.CodeTypeEnum;
import com.xspaceagi.system.spec.exception.BizException;
import com.xspaceagi.system.spec.utils.I18nUtil;
import com.xspaceagi.system.spec.utils.JwtUtils;
import com.xspaceagi.system.spec.utils.RedisUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    /**
     * 过期 token 检查时，单次 MGET / HDEL 携带的最大 key 数
     */
    private static final int TOKEN_CHECK_BATCH_SIZE = 200;

    @Resource
    private UserApplicationService userApplicationService;

    @Resource
    private VerifyCodeSendAndCheckService verifyCodeSendAndCheckService;

    @Resource
    private WeChatMpService weChatMpService;

    @Resource
    private RedisUtil redisUtil;

    @Value("${jwt.secretKey}")
    private String jwtSecretKey;

    @Override
    public String loginWithCode(String emailOrPhone, String code) {
        Assert.notNull(emailOrPhone, "emailOrPhone must be non-null");
        UserDto userDto;
        String phone = null;
        String email = null;
        // Verify email format
        if (emailOrPhone.matches("^[a-zA-Z0-9._-]+@[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)+$")) {
            verifyCodeSendAndCheckService.checkEmailCode(CodeTypeEnum.LOGIN_OR_REGISTER, emailOrPhone, code);
            userDto = userApplicationService.queryUserByEmail(emailOrPhone);
            email = emailOrPhone;
        } else {
            verifyCodeSendAndCheckService.checkPhoneCode(CodeTypeEnum.LOGIN_OR_REGISTER, emailOrPhone, code);
            userDto = userApplicationService.queryUserByPhone(emailOrPhone);
            phone = emailOrPhone;
        }
        if (userDto == null) {
            TenantConfigDto tenantConfigDto = (TenantConfigDto) RequestContext.get().getTenantConfig();
            if (tenantConfigDto.getOpenRegister() != null && tenantConfigDto.getOpenRegister() == 0) {
                throw new BizException(I18nUtil.systemMessage("Backend.Auth.Login.RegistrationClosed"));
            }
            userDto = new UserDto();
            userDto.setPhone(phone);
            userDto.setEmail(email);
            userApplicationService.add(userDto);
        }
        if (userDto.getStatus() == User.Status.Disabled || userDto.getStatus() == User.Status.Deleted) {
            throw new BizException(I18nUtil.systemMessage("Backend.Auth.Login.AccountDisabled"));
        }
        updateLastLoginTime(userDto);
        return createToken(userDto, UUID.randomUUID().toString().replace("-", ""));
    }

    @Override
    public String loginWithMpCode(String code) {
        //获取小程序用户手机号
        String phone = weChatMpService.getPhoneNumber(code);
        UserDto userDto = userApplicationService.queryUserByPhone(phone);
        if (userDto == null) {
            TenantConfigDto tenantConfigDto = (TenantConfigDto) RequestContext.get().getTenantConfig();
            if (tenantConfigDto.getOpenRegister() != null && tenantConfigDto.getOpenRegister() == 0) {
                throw new BizException(I18nUtil.systemMessage("Backend.Auth.Login.RegistrationClosed"));
            }
            userDto = new UserDto();
            userDto.setPhone(phone);
            userApplicationService.add(userDto);
        }
        if (userDto.getStatus() == User.Status.Disabled) {
            throw new BizException(I18nUtil.systemMessage("Backend.Auth.Login.AccountDisabled"));
        }
        updateLastLoginTime(userDto);
        return createToken(userDto, UUID.randomUUID().toString().replace("-", ""));
    }

    private void updateLastLoginTime(UserDto userDto) {
        UserDto update = new UserDto();
        update.setId(userDto.getId());
        update.setLastLoginTime(new Date());
        if (StringUtils.isNotBlank(RequestContext.get().getLang())) {
            update.setLang(RequestContext.get().getLang());
        }
        userApplicationService.update(update);
    }

    public String createToken(UserDto userDto, String clientId) {
        //过期token检查（批量清理，避免逐条 get 造成 N+1 导致登录极慢）
        cleanExpiredTokens(userDto.getId());
        TenantConfigDto tenantConfigDto = (TenantConfigDto) RequestContext.get().getTenantConfig();
        int expire = (int) (tenantConfigDto.getAuthExpire() == null ? 86400 : tenantConfigDto.getAuthExpire() * 60);
        String token = JwtUtils.createJwt(String.valueOf(userDto.getId()), userDto.getPhone(), jwtSecretKey, expire, new HashMap<>());
        redisUtil.set("token:" + token, userDto.getId().toString(), expire);
        redisUtil.hashPut("user-token:" + userDto.getId(), token, clientId);
        redisUtil.expire("user-token:" + userDto.getId(), expire);
        return token;
    }

    /**
     * 清理该用户下已失效的历史 token。
     * <p>
     * 原实现为「每条 token 一次 GET」的 N+1 调用，历史 token 积累到上千条时，
     * 在应用与 Redis 跨网/高 RTT 的部署下会把登录拖到分钟级甚至超时；
     * 且登录越慢越不容易完成清理，形成自我恶化。
     * <p>
     * 改为：HKEYS 一次 + MGET 分批 + HDEL 分批，网络往返从 O(n) 降到 O(n/batch)。
     */
    private void cleanExpiredTokens(Long userId) {
        String userTokenKey = "user-token:" + userId;
        Set<Object> historyTokens = redisUtil.hashKeys(userTokenKey);
        if (historyTokens == null || historyTokens.isEmpty()) {
            return;
        }
        List<String> tokens = historyTokens.stream().map(String::valueOf).collect(Collectors.toList());
        List<String> expired = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i += TOKEN_CHECK_BATCH_SIZE) {
            List<String> batch = tokens.subList(i, Math.min(i + TOKEN_CHECK_BATCH_SIZE, tokens.size()));
            List<String> keys = batch.stream().map(t -> "token:" + t).collect(Collectors.toList());
            List<Object> values = redisUtil.multiGet(keys);
            if (values.size() != batch.size()) {
                // 结果数量对不上时跳过该批次，避免误删有效 token
                log.warn("cleanExpiredTokens skip batch: userId={}, batch={}, values={}", userId, batch.size(), values.size());
                continue;
            }
            for (int j = 0; j < values.size(); j++) {
                if (values.get(j) == null) {
                    expired.add(batch.get(j));
                }
            }
        }
        if (expired.isEmpty()) {
            return;
        }
        for (int i = 0; i < expired.size(); i += TOKEN_CHECK_BATCH_SIZE) {
            List<String> batch = expired.subList(i, Math.min(i + TOKEN_CHECK_BATCH_SIZE, expired.size()));
            redisUtil.delete(userTokenKey, batch.toArray(new String[0]));
        }
        log.info("cleanExpiredTokens: userId={}, history={}, removed={}", userId, tokens.size(), expired.size());
    }

    @Override
    public String loginWithPassword(String emailOrPhone, String password) {
        Assert.notNull(emailOrPhone, "emailOrPhone must be non-null");
        UserDto userDto = userApplicationService.queryUserByPhoneOrEmailWithPassword(emailOrPhone, password);
        if (userDto != null) {
            updateLastLoginTime(userDto);
            return createToken(userDto, UUID.randomUUID().toString().replace("-", ""));
        }
        throw new BizException(I18nUtil.systemMessage("Backend.Auth.Login.UserNotFoundOrPasswordError"));
    }

    @Override
    public UserDto getLoginUserInfo(String token) {
        if (token == null) {
            return null;
        }
        Object val = redisUtil.get("token:" + token);
        if (val == null) {
            return null;
        }
        try {
            Long userId = Long.valueOf(val.toString());
            return userApplicationService.queryById(userId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public String refreshToken(String token) {
        UserDto userDto = getLoginUserInfo(token);
        if (userDto != null) {
            // 设置原来的token 10秒后过期
            redisUtil.expire("token:" + token, 10);
            String clientId = getClientId(userDto.getId(), token);
            redisUtil.hashDelete("user-token:" + userDto.getId(), token);
            String newToken = createToken(userDto, clientId);
            Object parentToken = redisUtil.get("token-parent:" + token);
            if (parentToken != null) {
                TenantConfigDto tenantConfigDto = (TenantConfigDto) RequestContext.get().getTenantConfig();
                int expire = (int) (tenantConfigDto.getAuthExpire() == null ? 86400 : tenantConfigDto.getAuthExpire() * 60);
                redisUtil.set("token-sub:" + parentToken, newToken, expire);
                redisUtil.set("token-parent:" + newToken, parentToken.toString(), expire);
                redisUtil.expire("token-parent:" + token, 0);
            }
            return newToken;
        }
        return token;
    }

    @Override
    public void expireToken(String token) {
        Object val = redisUtil.get("token:" + token);
        if (val != null) {
            redisUtil.hashDelete("user-token:" + val, token);
        }
        redisUtil.expire("token:" + token, 0);
    }

    @Override
    public void renewToken(String token) {
        TenantConfigDto tenantConfigDto = (TenantConfigDto) RequestContext.get().getTenantConfig();
        int expire = (int) (tenantConfigDto.getAuthExpire() == null ? 86400 : tenantConfigDto.getAuthExpire() * 60);
        redisUtil.expire("token:" + token, expire);
    }

    @Override
    public void expireUserAllToken(Long userId) {
        Map<String, Object> map = redisUtil.hashGetAll("user-token:" + userId);
        if (map != null) {
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                redisUtil.expire("token:" + entry.getKey(), 0);
            }
        }
        redisUtil.expire("user-token:" + userId, 0);
    }

    @Override
    public void expireUserAllToken(Long userId, String clientIdPrefix) {
        Map<String, Object> map = redisUtil.hashGetAll("user-token:" + userId);
        if (map != null) {
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (entry.getValue() != null && entry.getValue().toString().startsWith(clientIdPrefix)) {
                    redisUtil.expire("token:" + entry.getKey(), 0);
                }
            }
        }
    }

    @Override
    public String getClientId(Long userId, String token) {
        Object val = redisUtil.hashGet("user-token:" + userId, token);
        if (val == null || val.toString().isEmpty()) {
            val = UUID.randomUUID().toString().replace("-", "");
            redisUtil.hashPut("user-token:" + userId, token, val);
        }
        return val.toString();
    }

    @Override
    public List<String> getUserClientIds(Long userId) {
        Map<String, Object> map = redisUtil.hashGetAll("user-token:" + userId);
        if (map != null) {
            return map.values().stream().map(Object::toString).collect(Collectors.toList());
        }
        return List.of();
    }

    @Override
    public String newTicket(UserDto userDto, String token) {
        Object subToken = redisUtil.get("token-sub:" + token);
        if (subToken == null) {
            String clientId = getClientId(userDto.getId(), token);
            subToken = createToken(userDto, clientId);
            TenantConfigDto tenantConfigDto = (TenantConfigDto) RequestContext.get().getTenantConfig();
            int expire = (int) (tenantConfigDto.getAuthExpire() == null ? 86400 : tenantConfigDto.getAuthExpire() * 60);
            redisUtil.set("token-parent:" + subToken, token, expire - 60);
            redisUtil.set("token-sub:" + token, subToken.toString(), expire - 60);
        }
        String ticket = UUID.randomUUID().toString().replace("-", "");
        redisUtil.set("ticket:" + ticket, subToken.toString(), 60);// 60秒后过期
        return ticket;
    }

    @Override
    public String getTokenByTicket(String ticket) {
        Object val = redisUtil.get("ticket:" + ticket);
        if (val != null) {
            redisUtil.expire("ticket:" + ticket, 0);
            return val.toString();
        }
        return null;
    }

    @Override
    public String newEcoToken(String tenantClientId, String tenantSecret, UserDto user) {
        TenantConfigDto tenantConfig = (TenantConfigDto) RequestContext.get().getTenantConfig();
        Map<String, String> data = Map.of(
                "clientId", tenantClientId,
                "clientSiteUrl", tenantConfig.getSiteUrl(),
                "userId", user.getId().toString(),
                "role", user.getRole().name()
        );
        log.info("newEcoToken: {}, userId {}, userName {}", data, user.getId(), user.getUserName());
        return JwtUtils.createJwt(String.valueOf(user.getId()), "user" + user.getId(), tenantSecret, 86400, data);
    }

}
