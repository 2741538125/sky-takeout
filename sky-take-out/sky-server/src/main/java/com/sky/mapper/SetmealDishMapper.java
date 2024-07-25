package com.sky.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.sky.annotation.AutoFill;
import com.sky.entity.Setmeal;
import com.sky.enumeration.OperationType;

@Mapper
public interface SetmealDishMapper {

    /**
     * 根据菜品id集合查询对应套餐的id集合
     * @param dishIds
     * @return
     */
    //select setmeal_id from setmeal_dish where dish_id in (1,2,3,4...) 动态sql
    List<Long> getSetmealIdsByDishIds(List<Long> dishIds);

    /**
     * 根据id修改套餐数据
     * @param setmeal
     */
    //update setmeal 
    @AutoFill(OperationType.UPDATE)
    void update(Setmeal setmeal);
}
