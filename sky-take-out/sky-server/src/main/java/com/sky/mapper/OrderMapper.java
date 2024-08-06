package com.sky.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.sky.entity.Orders;

@Mapper
public interface OrderMapper {

    /**
     * 插入订单数据
     * @param orders
     */
    void insert(Orders orders);

}
