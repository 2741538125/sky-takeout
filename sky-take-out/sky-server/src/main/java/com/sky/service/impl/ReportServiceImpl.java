package com.sky.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.vo.OrderReportVO;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ReportServiceImpl implements ReportService{

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private UserMapper userMapper;


    /**
     * 统计指定时间区间内的营业额数据
     * @param begin
     * @param end
     * @return
     */
    public TurnoverReportVO getTurnoverStatistics(LocalDate begin, LocalDate end) {
        //用于存放begin到end的所有日期
        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);
        while (!begin.equals(end)) {
            begin = begin.plusDays(1);
            dateList.add(begin);
        }
        //通过此方法可以把list中的每一个元素取出，并且按照分割符“ ，”组成一个新的字符串
        String dateListString = StringUtils.join(dateList, ",");

        //用于存放对应天数的营业额
        List<Double> turnoverList = new ArrayList<>();
        for(LocalDate date : dateList) {
            //查询date日期对应的营业额数据，即当天的已完成的订单数据
            //需要计算date的开始时间，需要提现到几分几秒
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);

            //select sum(amount) from orders where order_time > ? and order_time < ? and status = 5
            Map map = new HashMap();
            map.put("begin", beginTime);
            map.put("end", endTime);
            map.put("status", Orders.COMPLETED);
            Double turnover = orderMapper.sumByMap(map);
            if(turnover == null) {
                turnover = 0.0;
            }
            turnoverList.add(turnover);
        }

        String turnoverListString = StringUtils.join(turnoverList, ",");

        //封装返回结果
        TurnoverReportVO turnoverReportVO = TurnoverReportVO.builder()
                                        .dateList(dateListString)
                                        .turnoverList(turnoverListString)
                                        .build();
        return turnoverReportVO;
    }



    /**
     * 统计指定时间内区间的用户数据
     * @param begin
     * @param end
     * @return
     */
    public UserReportVO getUserStatistics(LocalDate begin, LocalDate end) {
        //用于存放begin到end的所有日期
        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);
        while (!begin.equals(end)) {
            begin = begin.plusDays(1);
            dateList.add(begin);
        }
        //通过此方法可以把list中的每一个元素取出，并且按照分割符“ ，”组成一个新的字符串
        String dateListString = StringUtils.join(dateList, ",");

        //用于存放总用户量
        //select count(id) from user where create_time < ?
        List<Integer> totalUserList = new ArrayList<>();
        //用于存放新增用户量
        //select count(id) from user where create_time < ? and create_time > ?
        List<Integer> newUserList = new ArrayList<>();

        for (LocalDate date : dateList) {
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);

            Map map = new HashMap();
            map.put("end", endTime);
            Integer totalUser = userMapper.countByMap(map);
            
            map.put("begin", beginTime);
            Integer newUser = userMapper.countByMap(map);

            totalUserList.add(totalUser);
            newUserList.add(newUser);

        }

        //封装返回结果
        String totalUserListString = StringUtils.join(totalUserList, ",");
        String newUserListString = StringUtils.join(newUserList, ",");
        
        UserReportVO userReportVO = UserReportVO.builder()
                                .dateList(dateListString)
                                .totalUserList(totalUserListString)
                                .newUserList(newUserListString)
                                .build();

        return userReportVO;
    }


    /**
     * 统计指定时间区间内的订单数量
     * @param begin
     * @param end
     * @return
     */
    public OrderReportVO getOrdersStatistics(LocalDate begin, LocalDate end) {
        //用于存放begin到end的所有日期
        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);
        while (!begin.equals(end)) {
            begin = begin.plusDays(1);
            dateList.add(begin);
        }
        //通过此方法可以把list中的每一个元素取出，并且按照分割符“ ，”组成一个新的字符串
        String dateListString = StringUtils.join(dateList, ",");

        //用于存放每日订单数
        //select count(id) from orders where order_time < ? and order_time > ?
        List<Integer> orderCountList = new ArrayList<>();
        //用于存放每日有效订单数
        //select count(id) from orders where order_time < ? and order_time > ? and status = 5
        List<Integer> validOrderCountList = new ArrayList<>();

        for(LocalDate date : dateList) {
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);

            //查询每天的订单数
            Integer orderCount = getOrderCount(beginTime, endTime, null);
            orderCountList.add(orderCount);

            //查询每天的有效订单数
            Integer validOrderCount = getOrderCount(beginTime, endTime, Orders.COMPLETED);
            validOrderCountList.add(validOrderCount);
        }

        //需要计算时间区间内的订单总数和有效订单总数
        Integer totalOrderCount = orderCountList.stream().reduce(Integer::sum).get();
        Integer validOrderCount = validOrderCountList.stream().reduce(Integer::sum).get();
        //计算订单完成率
        Double orderCompletionRate = 0.0;
        if(totalOrderCount != 0) {
            orderCompletionRate = validOrderCount.doubleValue() / totalOrderCount;
        }
        
        return OrderReportVO.builder()
                            .dateList(dateListString)
                            .orderCountList(StringUtils.join(orderCountList, ","))
                            .validOrderCountList(StringUtils.join(validOrderCountList, ","))
                            .totalOrderCount(totalOrderCount)
                            .validOrderCount(validOrderCount)
                            .orderCompletionRate(orderCompletionRate)
                            .build();
    }

    private Integer getOrderCount(LocalDateTime begin, LocalDateTime end, Integer status) {

        Map map = new HashMap();
        map.put("begin", begin);
        map.put("end", end);
        map.put("status",status);

        return orderMapper.countByMap(map);
    }

}
