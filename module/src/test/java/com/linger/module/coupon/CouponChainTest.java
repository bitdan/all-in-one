package com.linger.module.coupon;

import com.linger.module.coupon.model.Coupon;
import com.linger.module.coupon.model.Order;
import com.linger.module.coupon.handler.DiscountHandler;
import com.linger.module.coupon.handler.FullReductionHandler;
import com.linger.module.coupon.handler.NoThresholdHandler;
import com.linger.module.coupon.service.CouponChainService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @version 1.0
 * @description CouponChainTest
 * @date 2025/7/24 16:58:04
 */
@Slf4j
public class CouponChainTest {

    private final CouponChainService chainService = new CouponChainService(Arrays.asList(
            new NoThresholdHandler(),
            new DiscountHandler(),
            new FullReductionHandler()
    ));

    @Test
    public void testFullReductionFirst() {
        log.info("\n\n====== 测试1: 满减券优先 ======");
        Order order = new Order(150.0);
        order.getCoupons().add(Coupon.createFullReduction(100.0, 20.0));
        order.getCoupons().add(Coupon.createDiscount(0.9));

        log.info("初始价格: {}元, 优惠券: [满100-20, 9折券]", order.getOriginalPrice());

        chainService.process(order);

        log.info("最终价格: {}元", order.getFinalPrice());
        assertEquals(117.0, order.getFinalPrice(), 0.01);
    }

    @Test
    public void testNoThresholdLast() {
        log.info("\n\n====== 测试2: 无门槛券最后 ======");
        Order order = new Order(50.0);
        order.getCoupons().add(Coupon.createNoThreshold(10.0));
        order.getCoupons().add(Coupon.createDiscount(0.8)); // 明确0.8折扣率

        log.info("初始价格: {}元, 优惠券: [10元无门槛券, 8折券]", order.getOriginalPrice());

        chainService.process(order);

        log.info("最终价格: {}元", order.getFinalPrice());
        assertEquals(30.0, order.getFinalPrice(), 0.01);
    }

    @Test
    public void testDiscountRounding() {
        log.info("\n\n====== 测试8: 折扣精度处理 ======");
        Order order = new Order(100.0);
        order.getCoupons().add(Coupon.createDiscount(0.00000001));
        order.getCoupons().add(Coupon.createDiscount(0.851)); // 约85折

        chainService.process(order);

        log.info("最终价格: {}元", order.getFinalPrice());
        assertEquals(0.01, order.getFinalPrice(), 0.01);
    }

    @Test
    public void testConditionNotMeet() {
        log.info("\n\n====== 测试3: 条件未满足 ======");
        Order order = new Order(90.0);
        order.getCoupons().add(Coupon.createFullReduction(100.0, 20.0));

        log.info("初始价格: {}元, 优惠券: [满100-20]", order.getOriginalPrice());

        chainService.process(order);

        log.info("最终价格: {}元 (未达到满减门槛)", order.getFinalPrice());
        assertEquals(90.0, order.getFinalPrice(), 0.01);
    }

    @Test
    public void testMultiTypeCombination() {
        log.info("\n\n====== 测试4: 多类型组合 ======");
        Order order = new Order(300.0);
        order.getCoupons().add(Coupon.createFullReduction(200.0, 50.0));
        order.getCoupons().add(Coupon.createDiscount(0.8));
        order.getCoupons().add(Coupon.createNoThreshold(10.0));

        log.info("初始价格: {}元, 优惠券: [满200-50, 8折券, 10元无门槛券]", order.getOriginalPrice());

        chainService.process(order);

        log.info("最终价格: {}元", order.getFinalPrice());
        assertEquals(190.0, order.getFinalPrice(), 0.01);
    }

    @Test
    public void testMultiSameTypeCoupons() {
        log.info("\n\n====== 测试5: 同类型多张券 ======");
        Order order = new Order(400.0);
        order.getCoupons().add(Coupon.createFullReduction(300.0, 100.0));
        order.getCoupons().add(Coupon.createFullReduction(200.0, 50.0));
        order.getCoupons().add(Coupon.createDiscount(0.9));
        order.getCoupons().add(Coupon.createDiscount(0.95));
        order.getCoupons().add(Coupon.createNoThreshold(20.0));
        order.getCoupons().add(Coupon.createNoThreshold(10.0));

        log.info("初始价格: {}元, 优惠券: [满300-100, 满200-50, 9折券, 95折券, 20元无门槛, 10元无门槛]",
                order.getOriginalPrice());

        chainService.process(order);

        log.info("最终价格: {}元", order.getFinalPrice());
        assertEquals(250.0, order.getFinalPrice(), 0.01);
    }

    @Test
    public void testZeroPriceProtection() {
        log.info("\n\n====== 测试6: 零价保护 ======");
        Order order = new Order(15.0);
        order.getCoupons().add(Coupon.createNoThreshold(20.0));


        log.info("初始价格: {}元, 优惠券: [10元无门槛券×2]", order.getOriginalPrice());

        chainService.process(order);

        log.info("最终价格: {}元 (避免负价格)", order.getFinalPrice());
        assertEquals(0.01, order.getFinalPrice(), 0.01);
    }

    @Test
    public void testInvalidCouponIgnored() {
        log.info("\n\n====== 测试7: 无效券被忽略 ======");
        Order order = new Order(80.0);
        // 折扣率无效的优惠券
        order.getCoupons().add(Coupon.createDiscount(1.5)); // 无效折扣
        order.getCoupons().add(Coupon.createNoThreshold(10.0));

        log.info("初始价格: {}元, 优惠券: [无效1.5折券, 10元无门槛券]", order.getOriginalPrice());

        chainService.process(order);

        log.info("最终价格: {}元", order.getFinalPrice());
        assertEquals(70.0, order.getFinalPrice(), 0.01);
    }
}
