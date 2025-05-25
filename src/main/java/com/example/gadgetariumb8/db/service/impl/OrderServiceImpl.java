package com.example.gadgetariumb8.db.service.impl;

import com.example.gadgetariumb8.db.dto.request.UserOrderRequest;
import com.example.gadgetariumb8.db.dto.response.*;
import com.example.gadgetariumb8.db.exception.exceptions.BadRequestException;
import com.example.gadgetariumb8.db.exception.exceptions.MessageSendingException;
import com.example.gadgetariumb8.db.exception.exceptions.NotFoundException;
import com.example.gadgetariumb8.db.model.Customer;
import com.example.gadgetariumb8.db.model.Order;
import com.example.gadgetariumb8.db.model.SubProduct;
import com.example.gadgetariumb8.db.model.User;
import com.example.gadgetariumb8.db.model.enums.Status;
import com.example.gadgetariumb8.db.repository.CustomOrderRepository;
import com.example.gadgetariumb8.db.repository.OrderRepository;
import com.example.gadgetariumb8.db.repository.SubProductRepository;
import com.example.gadgetariumb8.db.repository.UserRepository;
import com.example.gadgetariumb8.db.service.OrderService;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;
import org.thymeleaf.TemplateEngine;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class OrderServiceImpl implements OrderService {
    private final OrderRepository orderRepository;
    private final JdbcTemplate jdbcTemplate;
    private final CustomOrderRepository customOrderRepository;
    private final SubProductRepository subProductRepository;
    private final UserRepository userRepository;
    private final JavaMailSender javaMailSender;
    private final TemplateEngine templateEngine;
    private final Configuration configuration;

    @Override
    public PaginationResponse<OrderResponse> getAllOrders(String keyWord, String status, LocalDate from, LocalDate before, int page, int pageSize) {
        String sql = customOrderRepository.getAllOrder();
        log.info("Getting all orders.");

        String dateClause = "";
        if (from != null && before != null) {
            if (from.isAfter(before)) {
                log.error("The from date must be earlier than the date before");
                throw new BadRequestException("The from date must be earlier than the date before");
            } else if (from.isAfter(LocalDate.now()) || before.isAfter(LocalDate.now())) {
                log.error("The date must be in the past tense");
                throw new BadRequestException("The date must be in the past tense");
            }
            dateClause = customOrderRepository.dateClauseFromBefore();
        } else if (from != null) {
            if (from.isAfter(LocalDate.now())) {
                log.error("The date must be in the past tense");
                throw new BadRequestException("The date must be in the past tense");
            }
            dateClause = customOrderRepository.dateClauseFrom();
        } else if (before != null) {
            if (before.isAfter(LocalDate.now())) {
                log.error("The date must be in the past tense");
                throw new BadRequestException("The date must be in the past tense");
            }
            dateClause = customOrderRepository.dateClauseBefore();
        }
        String keyWordCondition = "";
        List<Object> params = new ArrayList<>();
        params.add(status);
        if (keyWord != null) {
            params.add("%" + keyWord + "%");
            params.add("%" + keyWord + "%");
            params.add("%" + keyWord + "%");
            params.add("%" + keyWord + "%");
            params.add("%" + keyWord + "%");
            keyWordCondition = customOrderRepository.keyWordCondition();
        }
        sql = String.format(sql, dateClause, keyWordCondition);

        int count = jdbcTemplate.queryForObject(customOrderRepository.countSql(sql), params.toArray(), Integer.class);
        int totalPage = (int) Math.ceil((double) count / pageSize);

        sql = sql + customOrderRepository.limitOffset();
        int offset = (page - 1) * pageSize;
        params.add(pageSize);
        params.add(offset);
        List<OrderResponse> orders = jdbcTemplate.query(sql, params.toArray(), (resultSet, i) -> new OrderResponse(
                resultSet.getLong("id"),
                resultSet.getString("fio"),
                resultSet.getString("orderNumber"),
                LocalDate.parse(resultSet.getString("createdAt")),
                resultSet.getInt("quantity"),
                resultSet.getBigDecimal("totalPrice"),
                resultSet.getBoolean("deliveryType"),
                resultSet.getString("status")
        ));
      log.info("Orders are successfully got!");
        return PaginationResponse.<OrderResponse>builder()
                .foundProducts(count)
                .elements(orders)
                .currentPage(page)
                .totalPages(totalPage)
                .build();
    }

    @Override
    public UserOrderResponse ordering(UserOrderRequest userOrderRequest) {
        List<SubProduct> subProducts = new ArrayList<>();
        BigDecimal totalPrice = new BigDecimal(0);
        int quantity = 0;
        for (Map.Entry<Long, Integer> p : userOrderRequest.productsIdAndQuantity().entrySet()) {
            SubProduct subProduct = subProductRepository.findById(p.getKey()).orElseThrow(
                    () -> new NotFoundException(String.format("Product with id %s is not found!", p.getKey())));
            subProducts.add(subProduct);
            BigDecimal price;
            if (subProduct.getDiscount() != null && subProduct.getDiscount().getPercent() > 0) {
                BigDecimal discountPercent = BigDecimal.valueOf(subProduct.getDiscount().getPercent());
                BigDecimal discountAmount = subProduct.getPrice().multiply(discountPercent).divide(BigDecimal.valueOf(100));
                price = subProduct.getPrice().subtract(discountAmount);
            } else {
                price = subProduct.getPrice();
            }
            if (p.getValue() > 0) {
                price = price.multiply(BigDecimal.valueOf(p.getValue()));
                quantity += p.getValue();
            }
            totalPrice = totalPrice.add(price);
        }

        Customer customer = new Customer();
        customer.setFirstName(userOrderRequest.customerInfo().firstName());
        customer.setLastName(userOrderRequest.customerInfo().lastName());
        customer.setEmail(userOrderRequest.customerInfo().email());
        customer.setPhoneNumber(userOrderRequest.customerInfo().phoneNumber());
        customer.setAddress(userOrderRequest.customerInfo().address());

        SecureRandom random = new SecureRandom();
        int randomNumber = random.nextInt(1000000, 9999999);

        Order order = new Order();
        order.setDate(LocalDate.now());
        order.setQuantity(quantity);
        order.setTotalPrice(totalPrice);
        order.setStatus(Status.PENDING);
        order.setDeliveryType(userOrderRequest.deliveryType());
        order.setPaymentType(userOrderRequest.paymentType());
        order.setOrderNumber(String.valueOf(randomNumber));
        order.addAllSubProducts(subProducts);
        order.setCustomer(customer);
        getAuthenticate().addOrder(order);

        Map<String, Object> model = new HashMap<>();
        model.put("orderNumber", order.getOrderNumber());
        model.put("dateOfOrder", order.getDate());
        model.put("statusOfOrder", "В ожидании");
        model.put("datePurchase", order.getDate());
        model.put("customer", order.getCustomer().getFirstName() + " " + order.getCustomer().getLastName());
        model.put("phoneNumber", order.getCustomer().getPhoneNumber());
        String deliveryType = "Самовывоз из магазина";
        if (order.isDeliveryType()) {
            deliveryType = "Доставка курьером";
        }
        model.put("deliveryType", deliveryType);
        model.put("link", "https://t.me/erkurss");

        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper mimeMessageHelper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name());
            Template template = configuration.getTemplate("order-email-template.html");
            String html = FreeMarkerTemplateUtils.processTemplateIntoString(template, model);
            mimeMessageHelper.setTo(order.getCustomer().getEmail());
            mimeMessageHelper.setText(html, true);
            mimeMessageHelper.setSubject("Gadgetarium");
            mimeMessageHelper.setFrom("Gadgetarium@gmail.com");
            javaMailSender.send(message);
        } catch (MessagingException | IOException | TemplateException e) {
            throw new MessageSendingException("Ошибка при отправке сообщения!");
        }

        return UserOrderResponse.builder()
                .httpStatus(HttpStatus.OK)
                .orderNumber(order.getOrderNumber())
                .message(String.format("""
                        Ваша заявка №%s от %s оформлена успешно.
                        Вся актуальная информация о статусе исполнения\s
                        заказа придет на указанный email:
                         %s""", order.getOrderNumber(), order.getDate(), order.getCustomer().getEmail()))
                .build();
    }

    private User getAuthenticate() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String login = authentication.getName();
        log.info("Token has been taken!");
        return userRepository.findUserInfoByEmail(login).orElseThrow(() -> {
            log.error("User not found!");
            return new NotFoundException("User not found!");
        }).getUser();
    }

    @Override
    public SimpleResponse changeStatusOfOrder(Long orderId, String status) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> {
            log.error(String.format("Order with id - %s is not found!", orderId));
            throw new NotFoundException(String.format("Order with id - %s is not found!", orderId));
        });
        Status newStatus;
        String statusRu;
        switch (status) {
            case "В ожидании" -> {
                newStatus = Status.PENDING;
                statusRu = "В ожидании";
            }
            case "Готов к выдаче" -> {
                newStatus = Status.READY_FOR_DELIVERY;
                statusRu = "Готов к выдаче";
            }
            case "Получен" -> {
                newStatus = Status.RECEIVED;
                statusRu = "Получен";
            }
            case "Отменить" -> {
                newStatus = Status.CANCEL;
                statusRu = "Отменен";
            }
            case "Курьер в пути" -> {
                newStatus = Status.COURIER_ON_THE_WAY;
                statusRu = "Курьер в пути";
            }
            case "Доставлен" -> {
                newStatus = Status.DELIVERED;
                statusRu = "Доставлен";
            }
            default -> {
                log.error("Status doesn't match!");
                return SimpleResponse.builder()
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .message("Status doesn't match!")
                        .build();
            }
        }
        order.setStatus(newStatus);

        Map<String, Object> model = new HashMap<>();
        model.put("orderNumber", order.getOrderNumber());
        model.put("dateOfOrder", order.getDate());
        model.put("statusOfOrder", statusRu);
        model.put("datePurchase", order.getDate());
        model.put("customer", order.getCustomer().getFirstName() + " " + order.getCustomer().getLastName());
        model.put("phoneNumber", order.getCustomer().getPhoneNumber());
        String deliveryType = "Самовывоз из магазина";
        if (order.isDeliveryType()) {
            deliveryType = "Доставка курьером";
        }
        model.put("deliveryType", deliveryType);
        model.put("link", "https://t.me/erkurss");
        model.put("dateOfChangeStatus", LocalDate.now());

        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper mimeMessageHelper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name());
            Template template = configuration.getTemplate("order-status-template.html");
            String html = FreeMarkerTemplateUtils.processTemplateIntoString(template, model);
            mimeMessageHelper.setTo(order.getCustomer().getEmail());
            mimeMessageHelper.setText(html, true);
            mimeMessageHelper.setSubject("Gadgetarium");
            mimeMessageHelper.setFrom("Gadgetarium@gmail.com");
            javaMailSender.send(message);
        } catch (MessagingException | IOException | TemplateException e) {
            log.error("Ошибка при отправке сообщения!");
            throw new MessageSendingException("Ошибка при отправке сообщения!");
        }

        return SimpleResponse.builder()
                .httpStatus(HttpStatus.OK)
                .message("Status changed successfully!")
                .build();
    }

    @Override
    public List<OrderGetByIdResponse> getByIdOrder(Long id) {
        if (!orderRepository.existsById(id)) {
            throw  new NotFoundException("Order with id = %s is not found".formatted(id));
        }
        String sql = """
                select concat(c.first_name,' ',c.last_name) as fullName,
                concat(o.order_number,'--',o.date) orderNumberAndDate,
                concat(p.name,' ',spc.characteristics, ' ', sp.colour) as productInfo,
                sp.quantity as quantity,
                sp.price as orderPrice,
                d.percent as orderPercent,
                case when d.percent <> 0 then (sp.price * d.percent / 100) end as sum_discount,
                o.total_price as totalPrice,
                o.status as status,
                c.phone_number as phoneNumber,
                c.address as address
                from orders o join products p on o.id = p.id
                join customers c on c.id = o.customer_id
                join orders_sub_products osp on o.id = osp.order_id
                join sub_products sp on p.id = sp.product_id
                join sub_product_characteristics spc on sp.id = spc.sub_product_id
                left join discounts d on sp.discount_id = d.id
                WHERE spc.characteristics_key  like 'память' and o.id = ?;
                """;
        List<OrderGetByIdResponse> orderGetByIdResponses = new ArrayList<>();
        jdbcTemplate.query(sql, (resulSet, i) -> {
            OrderGetByIdResponse order = new OrderGetByIdResponse();
            order.setFullName(resulSet.getString("fullName"));
            order.setOrderNumber(resulSet.getString("orderNumberAndDate"));
            order.setOrderNameInfo(resulSet.getString("productInfo"));
            order.setQuantity(resulSet.getInt("quantity"));
            order.setPrice(resulSet.getBigDecimal("orderPrice"));
            order.setDiscount(resulSet.getInt("orderPercent"));
            order.setTotalDiscount(resulSet.getInt("sum_discount"));
            order.setTotalFinal(resulSet.getInt("totalPrice"));
            order.setOrderStatus(resulSet.getString("status"));
            order.setPhoneNumber(resulSet.getString("phoneNumber"));
            order.setFullAddress(resulSet.getString("address"));
            orderGetByIdResponses.add(order);
            return order;}, id );
        return orderGetByIdResponses;
    }
       
    @Override                                   
    public SimpleResponse delete(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(
                () -> new NotFoundException("Order with id %s is not found.".formatted(orderId)));
        if (!order.getStatus().equals(Status.DELIVERED)
                && !order.getStatus().equals(Status.CANCEL)
                && !order.getStatus().equals(Status.RECEIVED)
        ) {
            throw new BadRequestException("""
                    Order with id %s cannot be deleted because it is not completed. Status of order - %s"""
                    .formatted(orderId, order.getStatus()));
        }
        orderRepository.deleteById(orderId);

        return SimpleResponse.builder()
                .httpStatus(HttpStatus.OK)
                .message("Order with id %s is deleted.".formatted(orderId))
                .build();
    }
}
