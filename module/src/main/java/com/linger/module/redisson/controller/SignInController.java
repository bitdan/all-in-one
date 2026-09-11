package com.linger.module.redisson.controller;

import com.linger.module.redisson.dto.MonthlySignInStatsResponse;
import com.linger.module.redisson.dto.SignInConsecutiveResponse;
import com.linger.module.redisson.dto.SignInRangeResponse;
import com.linger.module.redisson.dto.SignInResponse;
import com.linger.module.redisson.dto.SignInStatusResponse;
import com.linger.module.redisson.dto.YearlySignInStatsResponse;
import com.linger.module.redisson.service.SignInService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 签到功能控制器
 */
@RestController
@RequestMapping("/api/signin")
@RequiredArgsConstructor
@Slf4j
public class SignInController {

    private static final DateTimeFormatter YEAR_MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private final SignInService signInService;

    /**
     * 用户签到
     */
    @PostMapping("/sign")
    public SignInResponse signIn(@RequestParam Long userId,
                                 @RequestParam(required = false) String date) {
        try {
            String signDate = date != null ? date : LocalDate.now().toString();

            String signResult = signInService.signIn(userId, signDate);
            int consecutiveDays = signInService.getConsecutiveSignInDays(userId, signDate);

            return SignInResponse.builder()
                    .success(true)
                    .message(signResult)
                    .consecutiveDays(consecutiveDays)
                    .signDate(signDate)
                    .build();
        } catch (Exception e) {
            log.error("签到异常，userId: {}", userId, e);
            return SignInResponse.builder()
                    .success(false)
                    .message("签到失败")
                    .build();
        }
    }

    /**
     * 查询签到状态
     */
    @GetMapping("/status")
    public SignInStatusResponse getSignInStatus(@RequestParam Long userId,
                                                @RequestParam(required = false) String date) {
        try {
            String queryDate = date != null ? date : LocalDate.now().toString();

            boolean isSigned = signInService.isSignedIn(userId, queryDate);
            int consecutiveDays = signInService.getConsecutiveSignInDays(userId, queryDate);

            return SignInStatusResponse.builder()
                    .success(true)
                    .isSigned(isSigned)
                    .consecutiveDays(consecutiveDays)
                    .queryDate(queryDate)
                    .build();
        } catch (Exception e) {
            log.error("查询签到状态异常，userId: {}", userId, e);
            return SignInStatusResponse.builder()
                    .success(false)
                    .message("查询失败")
                    .build();
        }
    }

    /**
     * 获取月度签到统计
     */
    @GetMapping("/stats/monthly")
    public MonthlySignInStatsResponse getMonthlyStats(@RequestParam Long userId,
                                                       @RequestParam(required = false) String yearMonth) {
        try {
            String queryMonth = yearMonth != null ? yearMonth :
                    LocalDate.now().format(YEAR_MONTH_FORMATTER);

            String stats = signInService.getMonthlySignInStats(userId, queryMonth);

            return MonthlySignInStatsResponse.builder()
                    .success(true)
                    .stats(stats)
                    .yearMonth(queryMonth)
                    .build();
        } catch (Exception e) {
            log.error("查询月度统计异常，userId: {}", userId, e);
            return MonthlySignInStatsResponse.builder()
                    .success(false)
                    .message("查询失败")
                    .build();
        }
    }

    /**
     * 获取年度签到统计
     */
    @GetMapping("/stats/yearly")
    public YearlySignInStatsResponse getYearlyStats(@RequestParam Long userId,
                                                     @RequestParam(required = false) Integer year) {
        try {
            int queryYear = year != null ? year : LocalDate.now().getYear();

            String stats = signInService.getYearlySignInStats(userId, queryYear);

            return YearlySignInStatsResponse.builder()
                    .success(true)
                    .stats(stats)
                    .year(queryYear)
                    .build();
        } catch (Exception e) {
            log.error("查询年度统计异常，userId: {}", userId, e);
            return YearlySignInStatsResponse.builder()
                    .success(false)
                    .message("查询失败")
                    .build();
        }
    }

    /**
     * 获取指定日期范围的签到记录
     */
    @GetMapping("/range")
    public SignInRangeResponse getSignInRange(@RequestParam Long userId,
                                               @RequestParam String startDate,
                                               @RequestParam String endDate) {
        try {
            String rangeStats = signInService.getSignInStatusRange(userId, startDate, endDate);

            return SignInRangeResponse.builder()
                    .success(true)
                    .rangeStats(rangeStats)
                    .startDate(startDate)
                    .endDate(endDate)
                    .build();
        } catch (Exception e) {
            log.error("查询签到范围异常，userId: {}, startDate: {}, endDate: {}",
                    userId, startDate, endDate, e);
            return SignInRangeResponse.builder()
                    .success(false)
                    .message("查询失败")
                    .build();
        }
    }

    /**
     * 获取连续签到天数
     */
    @GetMapping("/consecutive")
    public SignInConsecutiveResponse getConsecutiveDays(@RequestParam Long userId,
                                                         @RequestParam(required = false) String date) {
        try {
            String queryDate = date != null ? date : LocalDate.now().toString();

            int consecutiveDays = signInService.getConsecutiveSignInDays(userId, queryDate);

            return SignInConsecutiveResponse.builder()
                    .success(true)
                    .consecutiveDays(consecutiveDays)
                    .queryDate(queryDate)
                    .build();
        } catch (Exception e) {
            log.error("查询连续签到天数异常，userId: {}", userId, e);
            return SignInConsecutiveResponse.builder()
                    .success(false)
                    .message("查询失败")
                    .build();
        }
    }
}
