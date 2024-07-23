package com.sky.service.impl;

import java.util.List;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sky.dto.DishDTO;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.mapper.DishFlavorMapper;
import com.sky.mapper.DishMapper;
import com.sky.service.DishService;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class DishServiceImpl implements DishService {

    //注入
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private DishFlavorMapper dishFlavorMapper;

    /**
     * 新增菜品和相应的口味
     * @param dishDTO
     */
    @Transactional //事务注解
    public void saveWithFlavor(DishDTO dishDTO) {
        
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO, dish); //属性命名一致才能copy


        //向菜品表中插入数据，仅一条
        dishMapper.insert(dish);

        //取得insert语句生成的dishID
        long dishId = dish.getId();

        //向口味表插入N条数据，口味可能有多种
        List<DishFlavor> flavors = dishDTO.getFlavors();
        if(flavors != null && flavors.size() > 0) {
            //遍历所有口味，设置上属于他的dishId
            flavors.forEach(dishFlavor ->{
                dishFlavor.setDishId(dishId);
            });
            //向口味表中批量插入数据
            dishFlavorMapper.insertBatch(flavors);
        }

    }
}
