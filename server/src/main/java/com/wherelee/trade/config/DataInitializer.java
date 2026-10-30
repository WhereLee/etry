package com.wherelee.trade.config;

import com.wherelee.trade.point.PointAccount;
import com.wherelee.trade.point.PointAccountMapper;
import com.wherelee.trade.point.PointFlow;
import com.wherelee.trade.point.PointFlowMapper;
import com.wherelee.trade.point.PointFlowType;
import com.wherelee.trade.user.User;
import com.wherelee.trade.user.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 首次启动时创建演示账号。
 * 密码哈希必须运行时生成，不能写进 SQL 种子文件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private static final long BUYER_INITIAL_POINTS = 50_000L;

    private final UserMapper userMapper;
    private final PointAccountMapper pointAccountMapper;
    private final PointFlowMapper pointFlowMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userMapper.count() > 0) {
            return;
        }
        createUser("buyer", "demo123", "体验用户", User.ROLE_USER, BUYER_INITIAL_POINTS);
        createUser("admin", "demo123", "管理员", User.ROLE_ADMIN, 0L);
        log.info("已初始化演示账号 buyer/demo123 与 admin/demo123，买家初始积分 {}", BUYER_INITIAL_POINTS);
    }

    private void createUser(String username, String rawPassword, String nickname, String role, long initialPoints) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setNickname(nickname);
        user.setRole(role);
        user.setTokenVersion(0);
        userMapper.insert(user);

        PointAccount account = new PointAccount();
        account.setUserId(user.getId());
        account.setBalance(initialPoints);
        pointAccountMapper.insert(account);

        if (initialPoints > 0) {
            PointFlow flow = new PointFlow();
            flow.setUserId(user.getId());
            flow.setType(PointFlowType.RECHARGE.name());
            flow.setAmount(initialPoints);
            flow.setBalanceAfter(initialPoints);
            pointFlowMapper.insert(flow);
        }
    }
}
