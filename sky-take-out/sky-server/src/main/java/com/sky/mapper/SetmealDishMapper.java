package com.sky.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.sky.annotation.AutoFill;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
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
     * 根据套餐的菜品数据集合，批量保存套餐和菜品的关系
     * @param setmealDishs
     */
    void insertBatch(List<SetmealDish> setmealDishs);

    /**
     * 根据setmealId删除套餐-菜品表中的数据
     * @param id
     */
    @Delete("delete from setmeal_dish where setmeal_id = #{setmealId}")
    void deleteBysetmealId(Long setmealId);

    /**
     * 根据setmealId查询套餐中的菜品数据集
     * @param id
     * @return
     */
    @Select("select * from setmeal_dish where setmeal_id = #{setmealId}")
    List<SetmealDish> getBysetmealId(Long setmealId);
}
