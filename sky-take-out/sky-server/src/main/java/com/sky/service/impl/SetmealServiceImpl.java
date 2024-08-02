package com.sky.service.impl;


import java.util.List;
import java.util.Set;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.exception.SetmealEnableFailedException;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.SetmealService;
import com.sky.vo.DishItemVO;
import com.sky.vo.SetmealVO;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class SetmealServiceImpl implements SetmealService{
    //注入
    @Autowired
    private SetmealMapper setmealMapper;
    @Autowired
    private SetmealDishMapper setmealDishMapper;
    @Autowired
    private DishMapper dishMapper;

    /**
     * 新增套餐，同时要保存套餐和菜品的关联关系
     * @param setmealDTO
     */
    @Transactional
    public void saveWithDish(SetmealDTO setmealDTO) {
        //首先要创建setmeal套餐信息
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);

        //向套餐表插入数据
        setmealMapper.insert(setmeal);

        //获取生成的套餐id
        Long setmealId = setmeal.getId();

        //从DTO中取出套餐中的菜品数据集合
        List<SetmealDish> setmealDishs = setmealDTO.getSetmealDishes();
        //对菜品数据集合中的每一个菜品数据中加入套餐id
        setmealDishs.forEach(setmealDish -> {
            setmealDish.setSetmealId(setmealId);
        });

        //保存套餐和菜品的关联关系
        setmealDishMapper.insertBatch(setmealDishs);

    }

    /**
     * 套餐分页查询
     * @param setmealPageQueryDTO
     * @return
     */
    public PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO) {
        
        PageHelper.startPage(setmealPageQueryDTO.getPage(), setmealPageQueryDTO.getPageSize());
        Page<SetmealVO> page = setmealMapper.pageQuery(setmealPageQueryDTO);

        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 批量删除套餐
     * @param ids
     */
    public void deteleBatch(List<Long> ids) {
        //首先要判断当前套餐集合是否能被删除，即集合中是否存在正在售卖的套餐
        for (Long id : ids) {
            Setmeal setmeal = setmealMapper.getById(id);
            if(setmeal.getStatus() == StatusConstant.ENABLE) {
                //如果当前套餐集合中有套餐正在售卖，则不能删除这批套餐
                throw new DeletionNotAllowedException(MessageConstant.SETMEAL_ON_SALE);
            }
        }

        //然后将setmeal表和setmealdish表中的数据删除
        for (Long id : ids) {
            //删除setmeal表中对应的套餐数据
            setmealMapper.deleteById(id);
            //删除setmeal_dish表中对应套餐的数据
            setmealDishMapper.deleteBysetmealId(id);
        }
        
    }

    /**
     * 根据id查询套餐和套餐中菜品的关联关系
     * @param id
     * @return
     */
    public SetmealVO getByIdWithDish(Long id) {
        //首先将套餐id中的菜品关联数据取出
        Setmeal setmeal = setmealMapper.getById(id);
        List<SetmealDish> setmealDishs = setmealDishMapper.getBysetmealId(id);

        //再将数据封装进SetmealVO中
        SetmealVO setmealVO = new SetmealVO();
        BeanUtils.copyProperties(setmeal, setmealVO);
        setmealVO.setSetmealDishes(setmealDishs);

        return setmealVO;
    }

    /**
     * 修改套餐
     * @param setmealDTO
     */
    public void update(SetmealDTO setmealDTO) {
        //首先将套餐表的数据取出
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);

        //其次，先更新setmeal表中的数据
        setmealMapper.update(setmeal);

        //然后，我们先删除套餐菜品表中关联该套餐的所有数据再重新插入
        Long setmealId = setmealDTO.getId();

        setmealDishMapper.deleteBysetmealId(setmealId);

        //再此重新插入套餐中菜品数据
        List<SetmealDish> setmealDishs = setmealDTO.getSetmealDishes();
        if(setmealDishs != null && setmealDishs.size() > 0) {
            for (SetmealDish setmealDish : setmealDishs) {
                setmealDish.setSetmealId(setmealId);
            }
        }

        //然后插入关系表
        setmealDishMapper.insertBatch(setmealDishs);
        
    }

    /**
     * 启售停售套餐
     * @param status
     * @param id
     */
    public void startOrStop(Integer status, Long id) {

        //如果是启售操作，套餐中有停售的菜品，则无法启售
        if(status == StatusConstant.ENABLE) {
            List<Dish> dishs = dishMapper.getBysetmealId(id);
            dishs.forEach(dish -> {
                if(dish.getStatus() == StatusConstant.DISABLE) {
                    throw new SetmealEnableFailedException(MessageConstant.SETMEAL_ENABLE_FAILED);
                }
            });
        }
        
        Setmeal setmeal = Setmeal.builder()
                        .status(status)
                        .id(id)
                        .build();
        
        setmealMapper.update(setmeal);
    }

    /**
     * 条件查询
     * @param setmeal
     * @return
     */
    public List<Setmeal> list(Setmeal setmeal) {
        List<Setmeal> list = setmealMapper.list(setmeal);
        return list;
    }

    /**
     * 根据id查询菜品选项
     * @param id
     * @return
     */
    public List<DishItemVO> getDishItemById(Long id) {
        return setmealMapper.getDishItemBySetmealId(id);
    }

}
