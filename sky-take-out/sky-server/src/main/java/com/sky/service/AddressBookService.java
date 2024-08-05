package com.sky.service;

import com.sky.entity.AddressBook;
import java.util.List;

public interface AddressBookService {

    /**
     * 动态查询地址信息
     * @param addressBook
     * @return
     */
    List<AddressBook> list(AddressBook addressBook);

    /**
     * 新增地址
     * @param addressBook
     */
    void save(AddressBook addressBook);

    /**
     * 根据地址id查询地址数据
     * @param id
     * @return
     */
    AddressBook getById(Long id);

    /**
     * 动态update
     * @param addressBook
     */
    void update(AddressBook addressBook);

    /**
     * 设置默认地址
     * @param addressBook
     */
    void setDefault(AddressBook addressBook);

    void deleteById(Long id);

}
