package com.sky.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.aspectj.internal.lang.annotation.ajcDeclarePrecedence;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersCancelDTO;
import com.sky.dto.OrdersConfirmDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersRejectionDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.AddressBook;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.entity.ShoppingCart;
import com.sky.entity.User;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.AddressBookMapper;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.mapper.UserMapper;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderOverViewVO;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.websocket.WebSocketServer;

import lombok.extern.slf4j.Slf4j;


@Service
@Slf4j
public class OrderServiceImpl implements OrderService{

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WeChatPayUtil weChatPayUtil;
    @Autowired
    private WebSocketServer webSocketServer;

    //将orders定义为全局变量
    private Orders orders_temp;
    //由于没真正执行paysuccess方法，得不到订单id，所以在新建订单时存储当前订单id

    /**
     * 用户下单
     * @param ordersSubmitDTO
     * @return
     */
    @Transactional
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
        
        //业务异常处理（地址簿为空，购物车数据为空）
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if(addressBook == null) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        ShoppingCart shoppingCart = new ShoppingCart();
        Long userId = BaseContext.getCurrentId();
        shoppingCart.setUserId(userId);
        List<ShoppingCart> shoppingCartlist = shoppingCartMapper.list(shoppingCart);
        if(shoppingCartlist == null && shoppingCartlist.size() == 0) {
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        //向订单表插入一条数据
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setNumber(String.valueOf(System.currentTimeMillis()));
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());
        orders.setUserId(userId);
        orders.setAddress(addressBook.getProvinceName() + addressBook.getCityName() + addressBook.getDistrictName() + addressBook.getDetail());

        orderMapper.insert(orders);

        List<OrderDetail> orderDetailList = new ArrayList<>();
        //向订单明细表插入n条数据
        for(ShoppingCart cart : shoppingCartlist) {
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cart, orderDetail);
            //需要在.xml文件中返回主键值，设置当前的订单明细关联的订单id
            orderDetail.setOrderId(orders.getId());
            orderDetailList.add(orderDetail);
        } 
        //批量插入n条数据
        orderDetailMapper.insertBatch(orderDetailList);

        //清空当前用户的购物车数据
        shoppingCartMapper.deleteByUser(userId);


        //封装VO的返回数据
        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                         .id(orders.getId())
                         .orderTime(orders.getOrderTime())
                         .orderNumber(orders.getNumber())
                         .orderAmount(orders.getAmount())
                         .build();

        //为orderid赋值
        this.orders_temp = orders;

        return orderSubmitVO;
    }

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // 当前登录用户id
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);

        // //调用微信支付接口，生成预支付交易单
        // JSONObject jsonObject = weChatPayUtil.pay(
        //         ordersPaymentDTO.getOrderNumber(), //商户订单号
        //         new BigDecimal(0.01), //支付金额，单位 元
        //         "苍穹外卖订单", //商品描述
        //         user.getOpenid() //微信用户的openid
        // );

        // if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
        //     throw new OrderBusinessException("该订单已支付");
        // }
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("code", "ORDERPAID");

        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));

        //为替代微信支付成功后的数据库订单状态更新，多定义一个方法进行修改
        Integer OrderPaidStatus = Orders.PAID;//支付状态，已支付
        Integer OrderStatus = Orders.TO_BE_CONFIRMED;//订单状态，待接单
        //发现没有将支付时间 check_out属性赋值，所以在这里更新
        LocalDateTime check_out_time = LocalDateTime.now();
        orderMapper.updateStatus(OrderStatus, OrderPaidStatus, check_out_time, orders_temp.getId());

        //由于我们跳过了微信支付的功能，所以不会回调到paySuccess，所以在这里向websocket推送消息
        //通过websocket向客户端浏览器推送消息 type orderId content
        Map map = new HashMap();
        map.put("type",1);
        map.put("orderId",this.orders_temp.getId());
        map.put("content","订单号："+this.orders_temp.getNumber());
        String json = JSON.toJSONString(map);
        webSocketServer.sendToAllClient(json);

        return vo;
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {

        // 根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);

        //由于我们跳过了微信支付的功能，所以不会回调到paySuccess
        //通过websocket向客户端浏览器推送消息 type orderId content
        Map map = new HashMap<>();
        map.put("type", 1); // 1表示来单提醒 2表示催单
        map.put("orderId", ordersDB.getId());
        map.put("content","订单号: " + outTradeNo);

        String json = JSON.toJSONString(map);
        webSocketServer.sendToAllClient(json); 
    }


    /**
     * 历史订单查询
     * @param page
     * @param pageSize
     * @param status
     * @return
     */
    public PageResult pageQueryByUser(int pageNum, int pageSize, Integer status) {
        

        //设置分页
        PageHelper.startPage(pageNum, pageSize);

        //封装成DTO形式，便于mapper
        OrdersPageQueryDTO ordersPageQueryDTO = new OrdersPageQueryDTO();

        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());
        ordersPageQueryDTO.setStatus(status);

        //分页条件查询
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);
        List<OrderVO> list = new ArrayList<>();

        if(page != null && page.getTotal() > 0) {

            for(Orders orders : page) {
                Long orderId = orders.getId();

                //查询订单详细信息
                List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orderId);
                //封装进OrderVO中
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                orderVO.setOrderDetailList(orderDetailList);
                list.add(orderVO);
            }

        }

        return new PageResult(page.getTotal(), list);
    }

    /**
     * 根据id查询订单详细信息
     * @param id
     * @return
     */
    public OrderVO details(Long id) {

        //后面需要数据封装进orderVO中
        OrderVO orderVO = new OrderVO();

        //查询订单以及订单详细信息
        Orders orders = orderMapper.getById(id);
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);

        //封装
        BeanUtils.copyProperties(orders, orderVO);
        orderVO.setOrderDetailList(orderDetailList);

        return orderVO;
    }


    /**
     * 用户端根据订单id取消订单
     * @param id
     */
    public void cancelByidWithUser(Long id) throws Exception {
        //  订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
        // - 待支付和待接单状态下，用户可直接取消订单
        // - 商家已接单状态下，用户取消订单需电话沟通商家
        // - 派送中状态下，用户取消订单需电话沟通商家
        // - 如果在待接单状态下取消订单，需要给用户退款
        // - 取消订单后需要将订单状态修改为“已取消”

        //获取订单信息
        Orders orders = orderMapper.getById(id);

        //订单是否存在
        if(orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        //是否是待支付和待接单状态下
        if(orders.getStatus() > 2) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders1 = new Orders();
        orders1.setId(orders.getId());
        //如果订单处于待接单状态，则已经付过款，则需要退款操作
        if(orders.getStatus() == 2) {
            //由于跳过了微信支付操作，只需要改数据库状态
            orders1.setPayStatus(Orders.REFUND);
        }
        //将数据封装进order1中
        orders1.setStatus(Orders.CANCELLED);
        orders1.setCancelReason("用户取消");
        orders1.setCancelTime(LocalDateTime.now());

        orderMapper.update(orders1);
        
    }


    /**
     * 再来一单
     * @param id
     */
    public void repetition(Long id) {
        // 再来一单就是将原订单中的商品重新加入到购物车中

        //后面信息补全要用
        Long userId = BaseContext.getCurrentId();
        // 取出上一单的订单详细信息
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);
        //将订单详细信息转换为购物车对象
        List<ShoppingCart> shoppingCartList = orderDetailList.stream().map(x -> {

            ShoppingCart shoppingCart = new ShoppingCart();
            //将订单详细信息复制到购物车对象中
            BeanUtils.copyProperties(x, shoppingCart, "id");
            //剩余信息补全
            shoppingCart.setUserId(userId);
            shoppingCart.setCreateTime(LocalDateTime.now());
            return shoppingCart;

        }).collect(Collectors.toList());

        shoppingCartMapper.insertBatch(shoppingCartList);

    }


    /**
     * 管理端订单分页查询
     * @param ordersPageQueryDTO
     * @return
     */
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        //某些订单需要额外返回订单菜品信息，将orders转为orderVO
        List<OrderVO> orderVOList = getOrderVOList(page);

        return new PageResult(page.getTotal(), orderVOList);
    }

    private List<OrderVO> getOrderVOList(Page<Orders> page) {

        List<OrderVO> orderVOList = new ArrayList<>();
        List<Orders> ordersList = page.getResult();

        if(ordersList != null && ordersList.size() > 0){

            for(Orders orders : ordersList) {
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                //将订单中的具体的菜品个数整合为String
                String orderDishes = getOrderDishesStr(orders);

                //将菜品数据封装入orderVO中
                orderVO.setOrderDishes(orderDishes);
                orderVOList.add(orderVO);
            }

        }

        return orderVOList;
    }

    private String getOrderDishesStr(Orders orders) {
        //获取订单详细信息
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());
        //将每条菜品的信息拼接成 xxxx*1份这种形式
        List<String> orderDishList = orderDetailList.stream().map(x -> {
            String orderDish = x.getName() + "*" + x.getNumber() + "份";
            return orderDish;
        }).collect(Collectors.toList());

        //String.join("", orderDishList) 的作用是将 orderDishList 中的所有元素连接成一个单一的字符串，中间不添加任何分隔符
        return String.join("", orderDishList);
    }


    /**
     * 各个状态的订单数量统计
     * @return
     */
    public OrderStatisticsVO statistics() {
        
        //统计个数
        //派送中
        Integer delivery = orderMapper.countStatus(Orders.DELIVERY_IN_PROGRESS);
        //待接单
        Integer toBeConfirmed = orderMapper.countStatus(Orders.TO_BE_CONFIRMED);
        //已接单
        Integer confirmed = orderMapper.countStatus(Orders.CONFIRMED);

        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        orderStatisticsVO.setToBeConfirmed(toBeConfirmed);
        orderStatisticsVO.setDeliveryInProgress(delivery);
        orderStatisticsVO.setConfirmed(confirmed);
        
        return orderStatisticsVO;
    }
    

    /**
     * 接单
     * @param ordersConfirmDTO
     */
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        Orders orders = Orders.builder()
                    .id(ordersConfirmDTO.getId())
                    .status(Orders.CONFIRMED)
                    .build();

        orderMapper.update(orders);
        
    }

    /**
     * 拒单
     * @param ordersCancelDTO
     */
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) throws Exception {
    //     - 商家拒单其实就是将订单状态修改为“已取消”
    //     - 只有订单处于“待接单”状态时可以执行拒单操作
    //     - 商家拒单时需要指定拒单原因
    //     - 商家拒单时，如果用户已经完成了支付，需要为用户退款 
        Orders orders = orderMapper.getById(ordersRejectionDTO.getId());

        if(orders == null || orders.getStatus() != 2) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        //如果用户已支付，则需要退款
        if(orders.getPayStatus() == Orders.PAID) {
            //由于跳过了微信支付环节，所以空

            log.info("需要退款...");
        }

        Orders orders1 = new Orders();
        orders1.setId(orders.getId());
        orders1.setStatus(Orders.CANCELLED);
        orders1.setRejectionReason(ordersRejectionDTO.getRejectionReason());
        orders1.setCancelTime(LocalDateTime.now());

        orderMapper.update(orders1);

    }


    /**
     * 取消订单
     * @param ordersCancelDTO
     */
    public void cancel(OrdersCancelDTO ordersCancelDTO)  {
        //查询订单数据
        Long id = ordersCancelDTO.getId();
        Orders orders = orderMapper.getById(id);

        //若已付款 则需要退款
        if(orders.getPayStatus() == 1) {
            //由于是个人商户，使用不了付款功能，跳过退款
            log.info("需要退款：{}", orders.getAmount());
        }

        //定义新的用于update的orders
        Orders orders1 = new Orders();
        orders1.setId(id);
        orders1.setStatus(Orders.CANCELLED);
        orders1.setCancelReason(ordersCancelDTO.getCancelReason());
        orders1.setCancelTime(LocalDateTime.now());

        orderMapper.update(orders1);
    }


    /**
     * 派送订单
     * @param id
     */
    public void delivery(Long id) throws Exception {

        Orders orders = orderMapper.getById(id);

        if(orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (!orders.getStatus().equals(Orders.CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders1 = new Orders();
        orders1.setId(id);
        orders1.setStatus(Orders.DELIVERY_IN_PROGRESS);

        orderMapper.update(orders1);
    }

    /**
     * 完成订单
     * @param id
     */
    public void complete(Long id) throws Exception {

        Orders orders = orderMapper.getById(id);

        if(orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (!orders.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders1 = new Orders();
        orders1.setId(id);
        orders1.setStatus(Orders.COMPLETED);
        //送达时间
        orders1.setDeliveryTime(LocalDateTime.now());

        orderMapper.update(orders1);
    }
    
    /**
     * 客户催单
     * @param id
     */
    public void reminder(Long id) {
        Orders orders = orderMapper.getById(id);

        if(orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        
        //通过websocket给商家的server端推送消息

        Map map = new HashMap<>();
        map.put("type", 2);
        map.put("orderId", id);
        map.put("content", "订单号" + orders.getNumber());

        String json = JSON.toJSONString(map);
        webSocketServer.sendToAllClient(json);

    }
}
