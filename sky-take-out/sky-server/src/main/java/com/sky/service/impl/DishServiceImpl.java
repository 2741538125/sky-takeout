package com.sky.service.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.entity.Setmeal;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.DishFlavorMapper;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.result.PageResult;
import com.sky.service.DishService;
import com.sky.vo.DishVO;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class DishServiceImpl implements DishService {

    //注入
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private DishFlavorMapper dishFlavorMapper;
    @Autowired
    private SetmealDishMapper setmealDishMapper;

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

    /**
     * 菜品分页查询
     * @param dishPageQueryDTO
     * @return
     */
    public PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO) {
               
        PageHelper.startPage(dishPageQueryDTO.getPage(), dishPageQueryDTO.getPageSize());
        Page<DishVO> page = dishMapper.pageQuery(dishPageQueryDTO);
        
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 批量删除菜品
     * @param ids
     */
    @Transactional  //事务注解，保证一致性
    public void deleteBatch(List<Long> ids) {
        //判断当前菜品是否能够删除，即是否存在起售中的菜品
        for (Long id : ids) {
            Dish dish =dishMapper.getById(id);
            if(dish.getStatus() == StatusConstant.ENABLE) {
                //如果当前菜品的状态是正在售卖中，则不能删除
               throw new DeletionNotAllowedException(MessageConstant.DISH_ON_SALE);
            }
        }

        //如果当前菜品被某个套餐关联了，也不能删除
        List<Long> setmealIds = setmealDishMapper.getSetmealIdsByDishIds(ids);
        if(setmealIds != null && setmealIds.size() > 0) {
            //当前菜品被套餐关联了，不能删除
            throw new DeletionNotAllowedException(MessageConstant.DISH_BE_RELATED_BY_SETMEAL);
        }

        // //删除菜品表中的菜品数据
        // for (Long id : ids) {
        //     dishMapper.deleteById(id);
        //     //删除菜品关联的口味数据
        //     dishFlavorMapper.deleteBydishId(id);
        // }
        

        //delete from dish where id in (1, 2, 3)
        //优化删除菜品数据的代码
        //根据菜品id集合批量删除菜品数据
        dishMapper.deleteByIds(ids);

        //根据菜品id集合批量删除口味表数据
        dishFlavorMapper.deleteBydishIds(ids);

    }

    /**
     * 根据id查询菜品和对应的口味数据
     * @param id
     * @return
     */
    public DishVO getByIdWithFlavor(Long id) {
        //需要查询两张表
        //先查询菜品表，获取菜品的基本属性
        Dish dish = dishMapper.getById(id);

        //再对应菜品查询对应的口味数据
        List<DishFlavor> dishFlavors = dishFlavorMapper.getByDishId(id);

        //将数据封装到Vo中
        DishVO dishVO = new DishVO();
        BeanUtils.copyProperties(dish, dishVO);
        dishVO.setFlavors(dishFlavors);

        return dishVO;
    }

    /**
     * 根据id修改菜品的基本信息和口味信息
     * @param dishDTO
     */
    @Transactional
    public void updateWithFlavor(DishDTO dishDTO) {
        //操作菜品表和口味表
        //先修改基本信息
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO, dish);

        dishMapper.update(dish);

        //修改口味数据有多种情况，我们可以在修改之前统一删除，再根据上传的数据做统一更新，以达到修改的效果
        //先删除所有口味数据
        dishFlavorMapper.deleteBydishId(dishDTO.getId());

        //再统一更新数据
        //向口味表插入N条数据，口味可能有多种
        List<DishFlavor> flavors = dishDTO.getFlavors();
        if(flavors != null && flavors.size() > 0) {
            //遍历所有口味，设置上属于他的dishId
            flavors.forEach(dishFlavor ->{
                dishFlavor.setDishId(dishDTO.getId());
            });
            //向口味表中批量插入数据
            dishFlavorMapper.insertBatch(flavors);
        }

    }

    /**
     * 启售禁售菜品
     * @param status
     * @param id
     */
    public void startOrStop(Integer status, Long id) {
        
        Dish dish = Dish.builder()
                    .status(status)
                    .id(id)
                    .build();
        
        dishMapper.update(dish);

        //如果是停售的操作，具有当前停售菜品的套餐也需要停售
        if(status == StatusConstant.DISABLE) {
            List<Long> dishIds = new ArrayList<>();
            dishIds.add(id);
            List<Long> setMealIds = setmealDishMapper.getSetmealIdsByDishIds(dishIds);
            if(setMealIds != null && setMealIds.size() > 0) {
                for (Long setMealId : setMealIds) {
                    Setmeal setmeal = Setmeal.builder()
                                    .id(setMealId)
                                    .status(StatusConstant.DISABLE)
                                    .build();
                                    
                    setmealDishMapper.update(setmeal);
                }
            }
        }

    }
}
