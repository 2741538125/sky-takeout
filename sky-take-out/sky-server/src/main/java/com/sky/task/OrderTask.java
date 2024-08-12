package com.sky.task;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;

import lombok.extern.slf4j.Slf4j;



/**
 * 定时任务类
 */

@Component
@Slf4j
public class OrderTask {

    @Autowired
    private OrderMapper orderMapper;

    /**
     * 处理超时订单
     */
    @Scheduled(cron = "0 * * * * ? ") //每分钟触发一次
    public void processTimeoutOrder() {
        log.info("定时处理超时订单：{}", LocalDateTime.now());

        //需要查询超时订单
        //select * from orders where order_time < ??? (当前时间 - 15min) and status = 1（待付款）
        LocalDateTime time = LocalDateTime.now().plusMinutes(-15);
        List<Orders> list = orderMapper.getByStatusAndOrdertimeLT(Orders.PENDING_PAYMENT, time);

        if(list != null && list.size() > 0) {

            for (Orders orders : list) {
                orders.setStatus(Orders.CANCELLED);
                orders.setCancelReason("订单超时");
                orders.setCancelTime(LocalDateTime.now());
                orderMapper.update(orders);
            }
        }
    }

    /**
     * 处理长期派送中订单
     */
    @Scheduled(cron = "0 0 1 * * ? ") //每天凌晨一点触发一次
    public void processDeliveryOrder() {
        log.info("处理长期派送中订单：{}", LocalDateTime.now());

        //需要查询派送中订单
        //select * from orders where order_time < ??? (当前时间 - 1hour) and status = ？（派送中）
        LocalDateTime time = LocalDateTime.now().plusMinutes(-60);
        List<Orders> list = orderMapper.getByStatusAndOrdertimeLT(Orders.DELIVERY_IN_PROGRESS, time);

        if(list != null && list.size() > 0) {

            for (Orders orders : list) {
                orders.setStatus(Orders.COMPLETED);
                orderMapper.update(orders);
            }
        }
    }

}
